import SwiftUI
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

    // MARK: Lifecycle

    /// Credentials live in the Keychain, so a signed-in account survives a quit.
    func restore() async {
        if !session.credentialStoreAvailable() {
            credentialWarning = "The Keychain is not available, so this session will be forgotten on quit."
        }
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
    @StateObject private var model = SessionModel()
    @State private var selection: String?

    var body: some View {
        Group {
            if model.phase == .signedOut {
                SignInView(model: model)
            } else {
                library
            }
        }
        .frame(minWidth: 820, minHeight: 560)
        .task { await model.restore() }
    }

    private var library: some View {
        NavigationSplitView {
            Sidebar(model: model)
                .navigationSplitViewColumnWidth(min: 180, ideal: 210, max: 280)
        } content: {
            MemoListView(model: model, selection: $selection)
                .navigationSplitViewColumnWidth(min: 260, ideal: 330, max: 460)
                .navigationTitle(model.activeTag.map { "#\($0)" } ?? "Memos")
                .navigationSubtitle(subtitle)
        } detail: {
            DetailPane(model: model, selection: selection)
        }
        .searchable(text: $model.query, placement: .toolbar, prompt: "Search memos")
        .onChange(of: model.query) { _, _ in Task { await model.reload() } }
        .onChange(of: model.activeTag) { _, _ in Task { await model.reload() } }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { Task { await model.sync() } } label: {
                    Image(systemName: "arrow.triangle.2.circlepath")
                }
                .help("Sync with the server")
                .disabled(model.isBusy)
            }
        }
    }

    private var subtitle: String {
        if case let .working(what) = model.phase { return what }
        if case let .failed(message) = model.phase { return message }
        return "\(model.memoCount) memos"
    }
}

// MARK: - Sidebar

struct Sidebar: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        List {
            Section("Library") {
                Label("All memos", systemImage: "tray.full")
                    .foregroundStyle(model.activeTag == nil ? Theme.accent : Theme.ink)
                    .onTapGesture { model.activeTag = nil }
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
                        .onTapGesture { model.activeTag = model.activeTag == tag ? nil : tag }
                    }
                }
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom) { accountFooter }
    }

    private var accountFooter: some View {
        VStack(alignment: .leading, spacing: 2) {
            Divider()
            HStack(spacing: 6) {
                Image(systemName: "person.crop.circle").foregroundStyle(Theme.inkSoft)
                VStack(alignment: .leading, spacing: 0) {
                    Text(model.displayName).font(.caption.weight(.medium)).lineLimit(1)
                    Text("Memos \(model.serverVersion)").font(.caption2).foregroundStyle(Theme.inkSoft)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
        }
    }
}

// MARK: - Detail

struct DetailPane: View {
    @ObservedObject var model: SessionModel
    let selection: String?
    @State private var detail: MemoDetail?

    var body: some View {
        Group {
            if let detail {
                MemoDetailView(detail: detail)
            } else {
                EmptyState(
                    icon: "doc.text",
                    title: "No memo selected",
                    detail: "Pick one from the list, or search to narrow it down."
                )
                .background(Theme.canvas)
            }
        }
        .task(id: selection) { await load() }
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
    var body: some Scene {
        WindowGroup("MyMemos") { RootView() }
            .defaultSize(width: 1040, height: 720)
            .windowToolbarStyle(.unified)
    }
}
