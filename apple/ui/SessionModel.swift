import SwiftUI
import UniformTypeIdentifiers
import Shared

// The session, shared by the macOS and iOS apps. Behind it is the same data layer the Android
// app runs: the sync engine, the outbox, the three-way merge and a Room database, shared rather
// than reimplemented. The views on each platform read and write this one object.

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
    /// Why the password is being asked for, so that supplying it finishes the job rather than
    /// just being remembered. Without this, asking and acting were two unconnected steps and
    /// anything that was not a reveal quietly did nothing.
    @Published var passwordRequest: PasswordRequest?
    @Published var wrongPassword = false
    /// Revealed text for locked memos, by localId. Display only: nothing stored changes.
    @Published var revealed: [String: String] = [:]
    @Published var pane: Pane = .memos
    /// Selection lives here rather than in a view because the menu bar acts on it.
    @Published var selection: String?
    @Published var editing: EditorTarget?
    @Published var sortByModified = false
    @Published var compactList = false
    @Published var templates: [TemplateRow] = []
    @Published var reminders: [ReminderRow] = []
    @Published var recurring: [RecurringRow] = []
    @Published var weeklyDigest = false
    @Published var digestNext = ""
    @Published var digestPreview = ""
    @Published var notificationsAllowed = false
    @Published var notificationsDenied = false
    /// Set by the menu bar, which has no view of its own to present a sheet from.
    @Published var settingReminderFor: String?
    let notifications = Notifications()
    @Published var mapTiles = false
    @Published var placed: [PlacedMemo] = []
    @Published var taskGroups: [TaskGroup] = []
    @Published var throwbacks: [Throwback] = []
    @Published var activeDays: Set<String> = []
    @Published var streak = 0
    @Published var avatar: PlatformImage?

    // What Android calls the rest of the app: archive, undo, the social layer, shortcuts,
    // sync state, accounts and tag styles. Loaded on demand; nothing here is on the launch path.
    @Published var showArchived = false
    @Published var undoable: Undoable?
    @Published var comments: [CommentRow] = []
    @Published var reactions: [ReactionRow] = []
    @Published var references: [ReferenceRow] = []
    @Published var backlinks: [MemoRow] = []
    @Published var shortcuts: [ShortcutRow] = []
    /// A shortcut whose results the timeline is showing instead of the timeline.
    @Published var activeShortcut: ShortcutRow?
    @Published var syncStatus: SyncStatusRow?
    @Published var failedOps: [FailedOpRow] = []
    @Published var conflicts: [MemoRow] = []
    @Published var accounts: [AccountRow] = []
    @Published var tagStyles: [String: TagStyleRow] = [:]
    /// Memos per tag over the whole account, not just what the timeline is showing.
    @Published var tagCounts: [String: Int] = [:]
    @Published var isAdmin = false
    @Published var unreadNotifications = 0
    @Published var sortCompletedTasks = false
    @Published var graph: GraphData?
    @Published var nearby: [NearbyRow] = []
    @Published var nearbyFailure: String?
    /// A one-line notice for something that could not be done, shown briefly.
    @Published var notice: String?

    /// Per-device, so it lives in UserDefaults rather than the synced settings memo.
    @Published var appearance: Appearance = .system {
        didSet {
            UserDefaults.standard.set(appearance.rawValue, forKey: "appearance")
            appearance.apply()
        }
    }

    /// The content column's screens. `memos` also covers the archive and a shortcut's results,
    /// which are the timeline with a different source.
    enum Pane: Hashable {
        case memos, tasks, review, reminders, templates
        case shortcuts, tags, notifications
        case profile, stats, tokens, webhooks
        case adminUsers, adminInstance
        case data
    }

    /// The sync sheet, opened from the toolbar, the menu bar or the library menu.
    @Published var showingSyncStatus = false

    /// The editor needs it directly for list continuation, which happens per keystroke and
    /// should not go through the model.
    let session = MemosSession()

    init() {
        // The shared cipher has no AES-GCM of its own on this platform; hand it CryptoKit's.
        HostCrypto.shared.provider = AppleCrypto()
        let stored = UserDefaults.standard.string(forKey: "appearance") ?? Appearance.system.rawValue
        appearance = Appearance(rawValue: stored) ?? .system
    }

    var openTaskCount: Int { taskGroups.reduce(0) { $0 + $1.tasks.count } }

    /// From the account-wide counts, so a tag filter or the archive does not skew it.
    func count(forTag tag: String) -> Int {
        tagCounts[tag] ?? sections.flatMap(\.memos).filter { $0.tags.contains(tag) }.count
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
        avatar = nil
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

    /// A view preference only, so there is nothing to reload: the same sections, drawn smaller.
    func setCompactList(_ enabled: Bool) async {
        try? await session.setCompactList(enabled: enabled)
        compactList = enabled
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

    // MARK: places

    /// The avatar, fetched once and kept for the session. Nil means show an initial.
    func loadAvatar() async {
        guard let bytes = try? await session.avatarBytes() else {
            avatar = nil
            return
        }
        avatar = PlatformImage(data: bytes.toData())
    }

    func loadPlaces() async {
        mapTiles = ((try? await session.mapTilesEnabled()) as? Bool) ?? false
        await TileLoader.shared.setEnabled(mapTiles)
        placed = (try? await session.locatedMemos()) ?? []
    }

    /// Turning tiles on is the user's call, and it is the moment anything reaches OSM.
    func setMapTiles(_ enabled: Bool) async {
        try? await session.setMapTiles(enabled: enabled)
        mapTiles = enabled
        await TileLoader.shared.setEnabled(enabled)
    }

    /// A day's located memos, oldest first, which is the order they were written in.
    func journey(for day: String) -> [PlacedMemo] {
        placed.filter { $0.dayKey == day }.reversed()
    }

    var journeyDays: [String] {
        Array(Set(placed.map(\.dayKey))).sorted(by: >)
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
        streak = ((try? await session.streak()).map { Int(truncating: $0) }) ?? 0
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

    /// Takes the password, then carries out whatever was waiting on it.
    func usePassword(_ password: String, remember: Bool) async {
        session.usePassword(password: password, remember: remember)
        wrongPassword = false
        let pending = passwordRequest
        passwordRequest = nil
        switch pending?.purpose {
        case .encrypt: await lock(pending!.localId, asking: false)
        case .removeEncryption: await unlockForGood(pending!.localId, asking: false)
        case .reveal, .none: await revealAll()
        }
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
        if result.needsPassword {
            passwordRequest = PasswordRequest(localId: localId, purpose: .reveal)
        }
    }

    /// After a password arrives, open everything already on screen that was waiting on it.
    private func revealAll() async {
        for memo in sections.flatMap(\.memos) where memo.locked {
            await reveal(memo.localId)
        }
    }

    /// Removes the encryption for good, so the server sees the text again.
    ///
    /// [asking] is false when this is the retry after a password has just been given, so a
    /// wrong password reopens the sheet rather than looping straight back into it.
    func unlockForGood(_ localId: String, asking: Bool = true) async {
        guard (try? await session.unlockForGood(localId: localId)) == true else {
            wrongPassword = !asking
            passwordRequest = PasswordRequest(localId: localId, purpose: .removeEncryption)
            return
        }
        revealed.removeValue(forKey: localId)
        await reload()
        await sync()
    }

    func lock(_ localId: String, asking: Bool = true) async {
        guard (try? await session.lock(localId: localId)) == true else {
            wrongPassword = !asking
            passwordRequest = PasswordRequest(localId: localId, purpose: .encrypt)
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
    /// Returns the memo's local id, so a new memo can have things attached to it afterwards.
    @discardableResult
    func save(editing: String?, text: String, visibility: String, pinned: Bool) async -> String? {
        do {
            let localId: String?
            if let editing {
                try await session.updateContent(localId: editing, content: text)
                if model(editing)?.pinned != pinned {
                    try await session.setPinned(localId: editing, pinned: pinned)
                }
                localId = editing
            } else {
                localId = try await session.create(content: text, visibility: visibility, pinned: pinned)
            }
            await reload()
            await sync()
            return localId
        } catch {
            phase = .failed(readable(error))
            return nil
        }
    }

    private func model(_ localId: String) -> MemoRow? { memo(localId) }

    func setPinned(_ localId: String, _ pinned: Bool) async {
        try? await session.setPinned(localId: localId, pinned: pinned)
        await reload()
        await sync()
    }

    /// Deletes, and offers Undo for a memo the server had, since that one waits a few seconds
    /// before it is sent. One that never synced is gone at once and Undo is not offered.
    func delete(_ localId: String) async {
        let title = memo(localId).map { $0.locked ? "Locked memo" : $0.title } ?? "Memo"
        let canUndo = ((try? await session.delete(localId: localId)) as? Bool) ?? false
        if selection == localId { selection = nil }
        undoable = canUndo ? Undoable(localId: localId, kind: .deleted, title: title) : nil
        await reload()
        if !canUndo { await sync() }
    }

    // MARK: reminders, templates and the digest

    func loadTemplates() async {
        templates = (try? await session.templates()) ?? []
    }

    func saveTemplate(id: Int64, title: String, body: String) async {
        try? await session.saveTemplate(id: id, title: title, body: body)
        await loadTemplates()
        await loadSchedules()
    }

    func deleteTemplate(_ id: Int64) async {
        try? await session.deleteTemplate(id: id)
        await loadTemplates()
        await loadSchedules()
    }

    /// A template's body with today's date written in, opened as a new memo ready to edit.
    func newFromTemplate(_ template: TemplateRow) {
        editing = EditorTarget(localId: nil, initialText: session.expandTemplate(body: template.body))
    }

    /// Notifications is its own object; a nested one does not republish, so what the views
    /// read lives here.
    func refreshNotificationPermission() async {
        await notifications.refreshPermission()
        notificationsAllowed = notifications.permission == .allowed
        notificationsDenied = notifications.permission == .denied
    }

    func requestNotificationPermission() async {
        await notifications.requestPermission()
        notificationsAllowed = notifications.permission == .allowed
        notificationsDenied = notifications.permission == .denied
        await loadSchedules()
    }

    func loadSchedules() async {
        reminders = (try? await session.reminders()) ?? []
        recurring = (try? await session.recurring()) ?? []
        weeklyDigest = ((try? await session.weeklyDigest()) as? Bool) ?? false
        digestNext = (try? await session.digestNextLabel()) ?? ""
        digestPreview = (try? await session.digestText()) ?? ""
        await applySchedules()
    }

    private func applySchedules() async {
        await notifications.sync(
            reminders: reminders,
            recurring: recurring,
            digest: weeklyDigest,
            digestHour: Int(session.digestHour)
        )
    }

    /// Returns false when the memo is not on the server yet, which is the one case that fails.
    func addReminder(_ memoLocalId: String, at date: Date, note: String) async -> Bool {
        let ok = ((try? await session.addReminder(
            memoLocalId: memoLocalId,
            atEpochMs: Int64(date.timeIntervalSince1970 * 1000),
            note: note
        )) as? Bool) ?? false
        if ok {
            await loadSchedules()
            await sync()
        }
        return ok
    }

    func removeReminder(_ id: String) async {
        _ = try? await session.removeReminder(id: id)
        await loadSchedules()
        await sync()
    }

    func setRecurring(_ title: String, hour: Int, minute: Int, enabled: Bool) async {
        try? await session.setRecurring(templateTitle: title, hour: Int32(hour), minute: Int32(minute), enabled: enabled)
        await loadSchedules()
        await sync()
    }

    func setWeeklyDigest(_ enabled: Bool) async {
        _ = try? await session.setWeeklyDigest(enabled: enabled)
        await loadSchedules()
        await sync()
    }

    /// Shows the digest for a Sunday that has gone by without one being shown.
    ///
    /// The scheduled notification can only carry words written a week earlier, which by the
    /// time it arrives are a week out of date. So that one is a nudge, and the real summary is
    /// posted here, from memos as they actually are. The date last shown is kept on this device
    /// rather than in the config memo: it is about this device having told you, not about the
    /// account.
    private func catchUpDigest() async {
        guard weeklyDigest else { return }
        guard let due = (try? await session.lastDigestDueEpochMs())?.int64Value else { return }
        let key = "digest.lastShownEpochMs"
        guard due > (UserDefaults.standard.object(forKey: key) as? Int64 ?? 0) else { return }

        let text = (try? await session.digestText()) ?? ""
        guard !text.isEmpty else { return }
        notifications.postNow(id: "digest.caught-up", title: "Your week in memos", body: text)
        UserDefaults.standard.set(due, forKey: key)
    }

    /// The catching up this device has to do, because the system fires the alarm but only the
    /// app can write the memo. Run at launch, after the first sync, so it sees what the phone did.
    func catchUp() async {
        let created = (try? await session.runDueRecurring()) ?? []
        if !created.isEmpty {
            await reload()
            await sync()
            for title in created {
                notifications.postNow(
                    id: "created.\(title).\(Date().timeIntervalSince1970)",
                    title: "Created from template",
                    body: title
                )
            }
        }
        await catchUpDigest()

        // A reminder whose moment passed while the app was shut is shown now and cleared, the
        // same as the phone does when its alarm fires.
        let now = Date().timeIntervalSince1970 * 1000
        for reminder in reminders where Double(reminder.atEpochMs) <= now {
            notifications.postNow(
                id: "reminder.late.\(reminder.id)",
                title: "Memo reminder",
                body: reminder.note.isEmpty ? reminder.memoTitle : reminder.note,
                memoLocalId: reminder.memoLocalId
            )
            await removeReminder(reminder.id)
        }
    }

    // MARK: Lifecycle

    /// Credentials live in the Keychain, so a signed-in account survives a quit.
    func restore() async {
        if !session.credentialStoreAvailable() {
            credentialWarning = "The Keychain is not available, so this session will be forgotten on quit."
        }
        sortByModified = ((try? await session.sortByModified()) as? Bool) ?? false
        compactList = ((try? await session.compactList()) as? Bool) ?? false
        sortCompletedTasks = ((try? await session.sortCompletedTasks()) as? Bool) ?? false
        mapTiles = ((try? await session.mapTilesEnabled()) as? Bool) ?? false
        await TileLoader.shared.setEnabled(mapTiles)
        phase = .working("Looking for a saved account")
        guard let who = try? await session.signedInAs(), !who.isEmpty else {
            phase = .signedOut
            return
        }
        displayName = who
        serverVersion = session.serverVersion
        isAdmin = ((try? await session.isAdmin()) as? Bool) ?? false
        Task { await loadAvatar() }
        await reload()
        await sync()
        await loadShortcuts()
        await loadAccounts()
        await refreshNotificationPermission()
        await loadTemplates()
        await loadSchedules()
        await catchUp()
    }

    func signIn() async {
        phase = .working("Signing in")
        do {
            try await session.signIn(serverUrl: server, username: username, password: password)
            displayName = session.displayName
            serverVersion = session.serverVersion
            password = ""
            Task { await loadAvatar() }
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
            await loadSyncStatus()
            await refreshUnread()
        } catch {
            phase = .failed(readable(error))
        }
    }

    /// Reads the local database. Everything on screen comes from here, never from the network.
    func reload() async {
        do {
            let found: [TimelineSection]
            if let shortcut = activeShortcut {
                found = try await session.runShortcut(name: shortcut.name)
            } else if showArchived {
                found = try await session.archived()
            } else if query.trimmingCharacters(in: .whitespaces).isEmpty {
                found = try await session.timeline()
            } else {
                found = try await session.search(query: query)
            }
            sections = filtered(found)
            tags = try await session.tags()
            await loadTagStyles()
            phase = .ready
            await writeWidgetSnapshot()
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
