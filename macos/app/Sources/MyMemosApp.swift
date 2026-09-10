import SwiftUI
import UniformTypeIdentifiers
import Shared

// The macOS client. Behind it is the same data layer the phone runs: the sync engine, the
// outbox, the three-way merge and a Room database, shared rather than reimplemented.
// Editing, attachments, export and backup are not built yet.

@MainActor
final class SessionModel: ObservableObject {
    enum Phase: Equatable {
        case signedOut
        case working(String)
        case ready
        case failed(String)
    }

    @Published var phase: Phase = .signedOut
    @Published var server = "https://memos.keltruc.com"
    @Published var username = ""
    @Published var password = ""
    @Published var sections: [TimelineSection] = []
    @Published var tags: [String] = []
    @Published var collapsed: Set<String> = []
    @Published var query = ""
    @Published var activeTag: String?
    @Published var serverVersion = ""
    @Published var displayName = ""
    @Published var credentialWarning: String?
    @Published var lastSynced: Date?
    @Published var askingForPassword = false
    @Published var wrongPassword = false
    /// Revealed text for locked memos, by localId. Display only: nothing stored changes.
    @Published var revealed: [String: String] = [:]
    @Published var pane: Pane = .memos
    /// Selection lives here rather than in a view because the menu bar acts on it.
    @Published var selection: String?
    @Published var editing: EditorTarget?
    @Published var sortByModified = false
    @Published var taskGroups: [TaskGroup] = []
    @Published var throwbacks: [Throwback] = []
    @Published var activeDays: Set<String> = []
    @Published var streak = 0

    enum Pane: Hashable { case memos, tasks, review }

    private let session = MemosSession()

    init() {
        // The shared cipher has no AES-GCM of its own on this platform; hand it CryptoKit's.
        MacCrypto.shared.provider = AppleCrypto()
    }

    var memoCount: Int { sections.reduce(0) { $0 + $1.memos.count } }

    var isBusy: Bool { if case .working = phase { return true } else { return false } }

    func memo(_ localId: String) -> MemoRow? {
        sections.lazy.flatMap(\.memos).first { $0.localId == localId }
    }

    /// Goes through the one session: building another would open a second database.
    func detail(for localId: String) async -> MemoDetail? {
        try? await session.memo(localId: localId)
    }

    // MARK: account and ordering

    var selectedMemo: MemoRow? { selection.flatMap { memo($0) } }

    func signOut() async {
        try? await session.signOut()
        sections = []
        tags = []
        selection = nil
        revealed.removeAll()
        displayName = ""
        phase = .signedOut
    }

    func setSortByModified(_ enabled: Bool) async {
        try? await session.setSortByModified(enabled: enabled)
        sortByModified = enabled
        await reload()
    }

    /// Opens a memo from another pane, putting the timeline back on screen.
    func open(_ localId: String) {
        selection = localId
        pane = .memos
    }

    func newMemo() { editing = EditorTarget(localId: nil) }

    func editSelected() {
        guard let selection, memo(selection)?.locked != true else { return }
        editing = EditorTarget(localId: selection)
    }

    // MARK: tasks and review

    func loadTasks() async {
        taskGroups = (try? await session.openTasks()) ?? []
    }

    func completeTask(_ memoLocalId: String, _ lineIndex: Int) async {
        try? await session.completeTask(memoLocalId: memoLocalId, lineIndex: Int32(lineIndex))
        await loadTasks()
        await reload()
        await sync()
    }

    func loadReview() async {
        streak = Int((try? await session.streak()) ?? 0)
        activeDays = Set((try? await session.activeDays()) ?? [])
        throwbacks = (try? await session.onThisDay()) ?? []
    }

    // MARK: attachments

    func attachments(_ localId: String) async -> [AttachmentRow] {
        (try? await session.attachments(localId: localId)) ?? []
    }

    /// Bytes for an attachment, local copy first, server second, then kept locally.
    func attachmentData(_ memoLocalId: String, _ attachmentLocalId: String) async -> Data? {
        guard let bytes = try? await session.attachmentBytes(
            memoLocalId: memoLocalId,
            attachmentLocalId: attachmentLocalId
        ) else { return nil }
        return bytes.toData()
    }

    /// Records picked files against a memo. The upload rides the outbox like any other change.
    func attach(_ memoLocalId: String, urls: [URL]) async {
        for url in urls {
            let type = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType
                ?? "application/octet-stream"
            _ = try? await session.attach(
                memoLocalId: memoLocalId,
                sourcePath: url.path,
                filename: url.lastPathComponent,
                mimeType: type
            )
        }
        await reload()
        await sync()
    }

    // MARK: locked memos

    var passwordRemembered: Bool { session.passwordRemembered }

    func usePassword(_ password: String, remember: Bool) {
        session.usePassword(password: password, remember: remember)
        wrongPassword = false
        Task { await revealAll() }
    }

    func forgetPassword() {
        session.forgetPassword()
        revealed.removeAll()
    }

    /// Shows a locked memo without changing it. Asks for the password if there is not one yet.
    func reveal(_ localId: String) async {
        guard let result = try? await session.reveal(localId: localId) else { return }
        if let text = result.text {
            revealed[localId] = text
            wrongPassword = false
            return
        }
        wrongPassword = result.wrongPassword
        if result.needsPassword { askingForPassword = true }
    }

    /// After a password arrives, open everything already on screen that was waiting on it.
    private func revealAll() async {
        for memo in sections.flatMap(\.memos) where memo.locked {
            await reveal(memo.localId)
        }
    }

    /// Removes the encryption for good, so the server sees the text again.
    func unlockForGood(_ localId: String) async {
        guard (try? await session.unlockForGood(localId: localId)) == true else {
            askingForPassword = true
            return
        }
        revealed.removeValue(forKey: localId)
        await reload()
        await sync()
    }

    func lock(_ localId: String) async {
        guard (try? await session.lock(localId: localId)) == true else {
            askingForPassword = true
            return
        }
        revealed.removeValue(forKey: localId)
        await reload()
        await sync()
    }

    func rawContent(_ localId: String) async -> String? {
        try? await session.rawContent(localId: localId)
    }

    /// Writes land in the local database and the outbox first, so this works with the network
    /// off; the sync afterwards is the push, not the save.
    func save(editing: String?, text: String, visibility: String, pinned: Bool) async {
        do {
            if let editing {
                try await session.updateContent(localId: editing, content: text)
                if model(editing)?.pinned != pinned {
                    try await session.setPinned(localId: editing, pinned: pinned)
                }
            } else {
                _ = try await session.create(content: text, visibility: visibility, pinned: pinned)
            }
            await reload()
            await sync()
        } catch {
            phase = .failed(readable(error))
        }
    }

    private func model(_ localId: String) -> MemoRow? { memo(localId) }

    func setPinned(_ localId: String, _ pinned: Bool) async {
        try? await session.setPinned(localId: localId, pinned: pinned)
        await reload()
        await sync()
    }

    func delete(_ localId: String) async {
        _ = try? await session.delete(localId: localId)
        await reload()
        await sync()
    }

    // MARK: Lifecycle

    /// Credentials live in the Keychain, so a signed-in account survives a quit.
    func restore() async {
        if !session.credentialStoreAvailable() {
            credentialWarning = "The Keychain is not available, so this session will be forgotten on quit."
        }
        sortByModified = ((try? await session.sortByModified()) as? Bool) ?? false
        phase = .working("Looking for a saved account")
        guard let who = try? await session.signedInAs(), !who.isEmpty else {
            phase = .signedOut
            return
        }
        displayName = who
        serverVersion = session.serverVersion
        await reload()
        await sync()
    }

    func signIn() async {
        phase = .working("Signing in")
        do {
            try await session.signIn(serverUrl: server, username: username, password: password)
            displayName = session.displayName
            serverVersion = session.serverVersion
            password = ""
            await sync()
        } catch {
            phase = .failed(readable(error))
        }
    }

    /// Pulls from the server into the local database, then reads the database back.
    func sync() async {
        phase = .working("Syncing")
        do {
            let outcome = try await session.sync()
            guard outcome == "ok" else {
                phase = .failed("Sync said: \(outcome)")
                return
            }
            lastSynced = Date()
            await reload()
        } catch {
            phase = .failed(readable(error))
        }
    }

    /// Reads the local database. Everything on screen comes from here, never from the network.
    func reload() async {
        do {
            let found = query.trimmingCharacters(in: .whitespaces).isEmpty
                ? try await session.timeline()
                : try await session.search(query: query)
            sections = filtered(found)
            tags = try await session.tags()
            phase = .ready
        } catch {
            phase = .failed(readable(error))
        }
    }

    private func filtered(_ found: [TimelineSection]) -> [TimelineSection] {
        guard let activeTag else { return found }
        return found.compactMap { section in
            let kept = section.memos.filter { $0.tags.contains(activeTag) }
            guard !kept.isEmpty else { return nil }
            return TimelineSection(key: section.key, label: section.label, memos: kept)
        }
    }

    func toggle(_ key: String) {
        if collapsed.contains(key) { collapsed.remove(key) } else { collapsed.insert(key) }
    }

    /// Kotlin exceptions arrive as NSError carrying the Kotlin one.
    private func readable(_ error: Error) -> String {
        let nsError = error as NSError
        if let underlying = nsError.userInfo["KotlinException"] as? Error {
            return String(describing: underlying)
        }
        return nsError.localizedDescription
    }
}

// MARK: - Root

struct RootView: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        Group {
            if model.phase == .signedOut {
                SignInView(model: model)
            } else {
                library
            }
        }
        .frame(minWidth: 860, minHeight: 580)
        .task { await model.restore() }
        .sheet(item: $model.editing) { target in
            EditorView(model: model, editing: target.localId)
        }
        .sheet(isPresented: $model.askingForPassword) {
            PasswordSheet(model: model)
        }
    }

    private var library: some View {
        NavigationSplitView {
            Sidebar(model: model)
                .navigationSplitViewColumnWidth(min: 180, ideal: 210, max: 280)
        } content: {
            content
                .navigationSplitViewColumnWidth(min: 280, ideal: 350, max: 520)
                .navigationTitle(title)
                .navigationSubtitle(subtitle)
        } detail: {
            DetailPane(model: model, selection: model.selection, edit: { model.editing = EditorTarget(localId: $0) })
        }
        .searchable(text: $model.query, placement: .toolbar, prompt: "Search memos")
        .onChange(of: model.query) { _, _ in Task { await model.reload() } }
        .onChange(of: model.activeTag) { _, _ in Task { await model.reload() } }
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                // No keyboard shortcuts here: those belong in the menu bar, where they are
                // discoverable and where the system can show them.
                Button { model.newMemo() } label: { Image(systemName: "square.and.pencil") }
                    .help("New memo")

                Button { model.editSelected() } label: { Image(systemName: "pencil") }
                    .help("Edit this memo")
                    .disabled(model.selectedMemo == nil || model.selectedMemo?.locked == true)

                Button { Task { await model.sync() } } label: {
                    Image(systemName: "arrow.triangle.2.circlepath")
                }
                .help("Sync with the server")
                .disabled(model.isBusy)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch model.pane {
        case .memos:
            MemoListView(model: model, selection: $model.selection)
        case .tasks:
            TasksView(model: model) { model.open($0) }
        case .review:
            ReviewView(model: model) { model.open($0) }
        }
    }

    private var title: String {
        switch model.pane {
        case .memos: return model.activeTag.map { "#\($0)" } ?? "Memos"
        case .tasks: return "Tasks"
        case .review: return "Review"
        }
    }

    private var subtitle: String {
        if case let .working(what) = model.phase { return what }
        if case let .failed(message) = model.phase { return message }
        switch model.pane {
        case .memos: return "\(model.memoCount) memos"
        case .tasks:
            let count = model.taskGroups.reduce(0) { $0 + $1.tasks.count }
            return "\(count) open across \(model.taskGroups.count) memos"
        case .review: return model.streak == 1 ? "1 day in a row" : "\(model.streak) days in a row"
        }
    }
}

// MARK: - Sidebar

struct Sidebar: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        List {
            Section("Library") {
                sidebarItem("All memos", "tray.full", pane: .memos, clearsTag: true)
                sidebarItem("Tasks", "checklist", pane: .tasks)
                sidebarItem("Review", "calendar.badge.clock", pane: .review)
            }
            if !model.tags.isEmpty {
                Section("Tags") {
                    ForEach(model.tags, id: \.self) { tag in
                        HStack {
                            Text("#\(tag)").font(.callout)
                            Spacer()
                        }
                        .contentShape(Rectangle())
                        .foregroundStyle(model.activeTag == tag ? Theme.accent : Theme.ink)
                        .onTapGesture {
                            model.activeTag = model.activeTag == tag ? nil : tag
                            model.pane = .memos
                        }
                    }
                }
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom) { accountFooter }
    }

    private func sidebarItem(_ title: String, _ symbol: String, pane: SessionModel.Pane, clearsTag: Bool = false) -> some View {
        let active = model.pane == pane && (!clearsTag || model.activeTag == nil)
        return Label(title, systemImage: symbol)
            .foregroundStyle(active ? Theme.accent : Theme.ink)
            .contentShape(Rectangle())
            .onTapGesture {
                model.pane = pane
                if clearsTag { model.activeTag = nil }
            }
    }

    private var accountFooter: some View {
        VStack(alignment: .leading, spacing: 0) {
            Divider()
            HStack(spacing: 8) {
                Image(systemName: "person.crop.circle")
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.inkSoft)
                VStack(alignment: .leading, spacing: 1) {
                    Text(model.displayName).font(Type.rowBody.weight(.medium)).lineLimit(1)
                    Text("Memos \(model.serverVersion)")
                        .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                }
                Spacer()
                if model.passwordRemembered {
                    Button {
                        model.forgetPassword()
                    } label: {
                        Image(systemName: "lock.rotation")
                    }
                    .buttonStyle(.borderless)
                    .help("Forget the memo password on this Mac")
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
        }
    }
}

// MARK: - Detail

/// Identifiable so a sheet can be driven by it; nil localId means a new memo.
struct EditorTarget: Identifiable {
    let localId: String?
    var id: String { localId ?? "new" }
}

struct DetailPane: View {
    @ObservedObject var model: SessionModel
    let selection: String?
    var edit: (String) -> Void = { _ in }
    @State private var detail: MemoDetail?

    var body: some View {
        Group {
            if let detail {
                MemoDetailView(detail: detail, model: model, edit: { edit(detail.row.localId) })
            } else {
                EmptyState(
                    icon: "doc.text",
                    title: "No memo selected",
                    detail: "Pick one from the list, or press ⌘N to write a new one."
                )
                .background(Theme.canvas)
            }
        }
        .task(id: selection) { await load() }
        .task(id: model.sections.count) { await load() }
    }

    private func load() async {
        guard let selection else {
            detail = nil
            return
        }
        detail = await model.detail(for: selection)
    }
}

// MARK: - Sign in

struct SignInView: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 6) {
                    Image(systemName: "text.alignleft")
                        .font(.system(size: 26, weight: .medium))
                        .foregroundStyle(Theme.accent)
                        .padding(10)
                        .background(Theme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
                    Text("MyMemos").font(.system(size: 30, weight: .bold))
                    Text("Connect to your Memos server")
                        .font(.callout).foregroundStyle(Theme.inkSoft)
                }

                VStack(alignment: .leading, spacing: 10) {
                    field("Server", text: $model.server, symbol: "server.rack")
                    field("Username", text: $model.username, symbol: "person")
                    secureField("Password", text: $model.password)
                }

                if let warning = model.credentialWarning {
                    Label(warning, systemImage: "exclamationmark.triangle")
                        .font(.caption).foregroundStyle(.orange)
                }
                if case let .failed(message) = model.phase {
                    Label(message, systemImage: "xmark.octagon")
                        .font(.caption).foregroundStyle(.red).textSelection(.enabled)
                }

                HStack {
                    if case let .working(what) = model.phase {
                        ProgressView().controlSize(.small)
                        Text(what).font(.caption).foregroundStyle(Theme.inkSoft)
                    }
                    Spacer()
                    Button("Sign in") { Task { await model.signIn() } }
                        .keyboardShortcut(.defaultAction)
                        .disabled(model.isBusy || model.username.isEmpty || model.password.isEmpty)
                }
            }
            .padding(32)
            .frame(width: 420)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Theme.hairline))
            .shadow(color: .black.opacity(0.08), radius: 20, y: 8)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.canvas)
    }

    private func field(_ label: String, text: Binding<String>, symbol: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: symbol).frame(width: 16).foregroundStyle(Theme.inkSoft)
            TextField(label, text: text).textFieldStyle(.roundedBorder)
        }
    }

    private func secureField(_ label: String, text: Binding<String>) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "lock").frame(width: 16).foregroundStyle(Theme.inkSoft)
            SecureField(label, text: text)
                .textFieldStyle(.roundedBorder)
                .onSubmit { Task { await model.signIn() } }
        }
    }
}

@main
struct MyMemosApp: App {
    @StateObject private var model = SessionModel()

    var body: some Scene {
        WindowGroup("MyMemos") { RootView(model: model) }
            .defaultSize(width: 1040, height: 720)
            .windowToolbarStyle(.unified)
            .commands { AppCommands(model: model) }

        Settings { SettingsView(model: model) }
    }
}
