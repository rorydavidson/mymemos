import SwiftUI
import Shared

/// Something just done that can be taken back, shown as a banner with an Undo button.
struct Undoable: Identifiable, Equatable {
    enum Kind { case deleted, archived }
    let localId: String
    let kind: Kind
    let title: String
    var id: String { localId }

    var message: String {
        switch kind {
        case .deleted: return "Deleted “\(title)”"
        case .archived: return "Archived “\(title)”"
        }
    }
}

// The rest of what Android does: archive, colours, the social layer, shortcuts, sync state,
// accounts, tag styles and the editor's completions. Each is a thin call into the session.
extension SessionModel {

    // MARK: archive, colour and undo

    func setArchived(_ localId: String, _ archived: Bool) async {
        let title = memo(localId).map { $0.locked ? "Locked memo" : $0.title } ?? "Memo"
        try? await session.setArchived(localId: localId, archived: archived)
        if archived {
            if selection == localId { selection = nil }
            undoable = Undoable(localId: localId, kind: .archived, title: title)
        }
        await reload()
        await sync()
    }

    func undo() async {
        guard let pending = undoable else { return }
        undoable = nil
        switch pending.kind {
        case .deleted:
            if ((try? await session.undoDelete(localId: pending.localId)) as? Bool) != true {
                notice = "Too late: the delete already reached the server."
            }
        case .archived:
            try? await session.setArchived(localId: pending.localId, archived: false)
        }
        await reload()
        await sync()
    }

    func dismissUndo() async {
        guard undoable != nil else { return }
        undoable = nil
        await sync()
    }

    func setColour(_ localId: String, _ name: String?) async {
        try? await session.setColour(localId: localId, name: name)
        await reload()
        await sync()
    }

    var colourOptions: [ColourOption] { session.colours() }

    // MARK: comments, reactions and references

    func loadSocial(_ localId: String) async {
        async let c = session.comments(localId: localId)
        async let r = session.reactions(localId: localId)
        async let refs = session.references(localId: localId)
        async let back = session.backlinks(localId: localId)
        comments = (try? await c) ?? []
        reactions = (try? await r) ?? []
        references = (try? await refs) ?? []
        backlinks = (try? await back) ?? []
    }

    func addComment(_ localId: String, _ text: String) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        if ((try? await session.addComment(localId: localId, text: trimmed)) as? Bool) != true {
            notice = "This memo has not reached the server yet. Sync, then try again."
            return
        }
        await loadSocial(localId)
        await sync()
        await loadSocial(localId)
    }

    func toggleReaction(_ localId: String, _ type: String) async {
        try? await session.toggleReaction(localId: localId, type: type)
        reactions = (try? await session.reactions(localId: localId)) ?? []
        await sync()
    }

    var quickReactions: [String] { session.quickReactions }

    func addReference(_ localId: String, to targetLocalId: String) async {
        try? await session.addReference(localId: localId, targetLocalId: targetLocalId)
        await loadSocial(localId)
        await sync()
    }

    func removeReference(_ localId: String, _ remoteName: String) async {
        try? await session.removeReference(localId: localId, remoteName: remoteName)
        await loadSocial(localId)
        await sync()
    }

    func referenceCandidates(_ query: String) async -> [MemoRow] {
        (try? await session.referenceCandidates(query: query)) ?? []
    }

    /// Opens a memo by its server name, pulling it first if this device has not seen it.
    func openRemote(_ remoteName: String) async {
        guard let localId = try? await session.localIdForRemote(remoteName: remoteName) else {
            notice = "That memo is not on this device and the server could not be reached."
            return
        }
        await reload()
        open(localId)
    }

    // MARK: share links

    func shares(_ localId: String) async -> [ShareRow] {
        (try? await session.shares(localId: localId)) ?? []
    }

    func createShare(_ localId: String, expiresInDays: Int) async -> ShareRow? {
        try? await session.createShare(localId: localId, expiresInDays: Int32(expiresInDays))
    }

    func revokeShare(_ localId: String, _ name: String) async {
        try? await session.revokeShare(localId: localId, name: name)
    }

    // MARK: shortcuts

    func loadShortcuts() async {
        shortcuts = (try? await session.shortcuts()) ?? []
    }

    /// Shows a shortcut's results in place of the timeline, or the timeline again for nil.
    func showShortcut(_ shortcut: ShortcutRow?) async {
        activeShortcut = shortcut
        showArchived = false
        activeTag = nil
        await reload()
    }

    func saveShortcut(name: String?, title: String, filter: String) async -> Bool {
        do {
            try await session.saveShortcut(name: name, title: title, filter: filter)
            await loadShortcuts()
            return true
        } catch {
            notice = readableMessage(error)
            return false
        }
    }

    func deleteShortcut(_ name: String) async {
        try? await session.deleteShortcut(name: name)
        if activeShortcut?.name == name { await showShortcut(nil) }
        await loadShortcuts()
    }

    // MARK: sync state

    func loadSyncStatus() async {
        syncStatus = try? await session.syncState()
        failedOps = (try? await session.failedOps()) ?? []
        conflicts = (try? await session.conflicts()) ?? []
    }

    func retryFailed() async {
        try? await session.retryFailed()
        await sync()
    }

    func keepConflictCopy(_ localId: String) async {
        try? await session.keepConflictCopy(localId: localId)
        await sync()
    }

    func reauthenticate(_ password: String) async -> Bool {
        let ok = ((try? await session.reauthenticate(password: password)) as? Bool) ?? false
        if ok { await sync() }
        return ok
    }

    // MARK: accounts

    func loadAccounts() async {
        accounts = (try? await session.accounts()) ?? []
    }

    func switchAccount(_ id: Int64) async {
        try? await session.switchAccount(id: id)
        displayName = session.displayName
        serverVersion = session.serverVersion
        selection = nil
        revealed.removeAll()
        isAdmin = ((try? await session.isAdmin()) as? Bool) ?? false
        await loadAccounts()
        Task { await loadAvatar() }
        await reload()
        await sync()
        await loadShortcuts()
        await loadSchedules()
    }

    func knownServers() async -> [String] {
        (try? await session.knownServers()) ?? []
    }

    func setSortCompletedTasks(_ enabled: Bool) async {
        try? await session.setSortCompletedTasks(enabled: enabled)
        sortCompletedTasks = enabled
    }

    // MARK: tag styles

    func loadTagStyles() async {
        let rows = (try? await session.tagStyles()) ?? []
        tagStyles = Dictionary(uniqueKeysWithValues: rows.map { ($0.tag, $0) })
    }

    func setTagStyle(_ tag: String, emoji: String?, colourName: String?) async {
        try? await session.setTagStyle(tag: tag, emoji: emoji, colourName: colourName)
        await loadTagStyles()
        await sync()
    }

    // MARK: tasks, completions, places, graph

    func toggleTask(_ localId: String, line: Int, checked: Bool) async {
        try? await session.toggleTask(localId: localId, lineIndex: Int32(line), checked: checked)
        await reload()
        await sync()
    }

    func tagSuggestions(_ prefix: String) async -> [String] {
        (try? await session.tagSuggestions(prefix: prefix)) ?? []
    }

    func dateSuggestions(_ prefix: String) -> [DateSuggestionRow] {
        session.dateSuggestions(prefix: prefix)
    }

    func setLocation(_ localId: String, latitude: Double, longitude: Double, placeName: String) async {
        try? await session.setLocation(localId: localId, latitude: latitude, longitude: longitude, placeName: placeName)
        await reload()
        await sync()
    }

    func clearLocation(_ localId: String) async {
        try? await session.clearLocation(localId: localId)
        await reload()
        await sync()
    }

    func loadNearby(latitude: Double, longitude: Double) async {
        nearby = (try? await session.nearby(latitude: latitude, longitude: longitude)) ?? []
        nearbyFailure = nil
    }

    func loadGraph() async {
        graph = try? await session.referenceGraph()
    }

    /// The memos of one day, for the day-by-day review. yyyy-MM-dd.
    func memosOn(_ isoDate: String) async -> [MemoRow] {
        (try? await session.memosOn(isoDate: isoDate)) ?? []
    }

    // MARK: extensions

    /// Anything the share extension left: each becomes a memo. Text opens the editor
    /// prefilled, as the Android share target does; images go with it.
    func drainSharedInbox() {
        #if os(iOS)
        guard phase != .signedOut, editing == nil else { return }
        let items = AppGroup.collect()
        guard let first = items.first else { return }
        let images = first.images.compactMap { AppGroup.container?.appendingPathComponent("inbox").appendingPathComponent($0) }
        editing = EditorTarget(localId: nil, initialText: first.text.isEmpty ? nil : first.text, initialImages: images)
        // Anything beyond the first is written straight away rather than queued behind a sheet.
        for item in items.dropFirst() where !item.text.isEmpty {
            Task { await save(editing: nil, text: item.text, visibility: "PRIVATE", pinned: false) }
        }
        #endif
    }

    /// What the widget shows, rewritten whenever the timeline is reloaded.
    func writeWidgetSnapshot() async {
        #if os(iOS)
        let recent = sections.flatMap(\.memos).prefix(10).map {
            AppGroup.Snapshot.Memo(localId: $0.localId, title: $0.locked ? "Locked memo" : $0.title, time: $0.timeLabel, pinned: $0.pinned)
        }
        let groups = (try? await session.openTasks()) ?? []
        let tasks = groups.flatMap { group in
            group.tasks.map { AppGroup.Snapshot.Task(memoLocalId: group.memoLocalId, line: Int($0.lineIndex), text: $0.text, due: $0.dueLabel, overdue: $0.overdue) }
        }.prefix(12)
        AppGroup.write(AppGroup.Snapshot(recent: Array(recent), tasks: Array(tasks), writtenAt: Date()))
        WidgetRefresh.reload()
        #endif
    }

    // MARK: URLs

    /// `mymemos://new?content=…&visibility=PRIVATE&pinned=false&open=false` writes a memo,
    /// or opens the editor prefilled when `open` is true; `mymemos://memo/<id>` opens one by
    /// local id or server name. The same shapes as the Android automation intent, so a
    /// Shortcut written for one platform reads the same on the other.
    func handle(url: URL) async {
        guard url.scheme == "mymemos", phase != .signedOut else { return }
        let parts = URLComponents(url: url, resolvingAgainstBaseURL: false)
        func query(_ name: String) -> String? { parts?.queryItems?.first { $0.name == name }?.value }

        switch url.host {
        case "new":
            let content = query("content") ?? ""
            if query("open") == "true" || content.isEmpty {
                editing = EditorTarget(localId: nil, initialText: content.isEmpty ? nil : content)
                return
            }
            let visibility = ["PRIVATE", "PROTECTED", "PUBLIC"].contains(query("visibility") ?? "") ? query("visibility")! : "PRIVATE"
            let id = await save(editing: nil, text: content, visibility: visibility, pinned: query("pinned") == "true")
            if let id { open(id) }
        case "memo":
            let id = url.lastPathComponent
            if memo(id) != nil { open(id) } else { await openRemote(id.hasPrefix("memos/") ? id : "memos/\(id)") }
        default:
            break
        }
    }

    /// A notification the user tapped names its memo; open it.
    func handleNotification(userInfo: [AnyHashable: Any]) {
        if let id = userInfo[Notifications.memoKey] as? String, !id.isEmpty { open(id) }
    }

    /// Kotlin exceptions arrive as NSError carrying the Kotlin one; say what it said.
    func readableMessage(_ error: Error) -> String {
        let nsError = error as NSError
        if let underlying = nsError.userInfo["KotlinException"] as? Error {
            return String(describing: underlying)
        }
        return nsError.localizedDescription
    }
}
