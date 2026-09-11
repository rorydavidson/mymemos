import SwiftUI
import Shared

// The macOS client's shell: the three-column window, its toolbar, and the app itself. The
// session model and the views inside the columns live in apple/ui and are shared with iOS.

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
        .task {
            model.appearance.apply()
            await model.restore()
        }
        .sheet(item: $model.editing) { target in
            EditorView(model: model, editing: target.localId, initialText: target.initialText)
        }
        .sheet(item: $model.passwordRequest) { request in
            PasswordSheet(model: model, request: request)
        }
        .sheet(item: Binding(
            get: { model.settingReminderFor.map(ReminderTarget.init) },
            set: { if $0 == nil { model.settingReminderFor = nil } }
        )) { target in
            ReminderSheet(model: model, memoLocalId: target.id)
        }
        .sheet(isPresented: $model.showingSyncStatus) {
            SyncStatusView(model: model)
        }        .alert("MyMemos", isPresented: Binding(get: { model.notice != nil }, set: { if !$0 { model.notice = nil } })) {
            Button("OK") { model.notice = nil }
        } message: {
            Text(model.notice ?? "")
        }
    }

    private var library: some View {
        NavigationSplitView {
            Sidebar(model: model)
                .navigationSplitViewColumnWidth(min: 180, ideal: 210, max: 280)
        } content: {
            content
                .navigationSplitViewColumnWidth(min: 300, ideal: 340, max: 480)
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
                SyncStatusButton(model: model)

                // No keyboard shortcuts here: those belong in the menu bar, where they are
                // discoverable and where the system can show them.
                ToolbarIcon(symbol: "pencil", help: "Edit this memo",
                            disabled: model.selectedMemo == nil || model.selectedMemo?.locked == true) {
                    model.editSelected()
                }
                ToolbarIcon(symbol: "square.and.pencil", help: "New memo") { model.newMemo() }

                ToolbarIcon(symbol: model.compactList ? "rectangle.grid.1x2" : "list.bullet",
                            help: model.compactList ? "Show full memo cards" : "Show one line per memo") {
                    Task { await model.setCompactList(!model.compactList) }
                }

                ToolbarMenu(symbol: model.appearance.symbol, help: "Light or dark") {
                    Picker("Appearance", selection: $model.appearance) {
                        ForEach(Appearance.allCases) { option in
                            Label(option.title, systemImage: option.symbol).tag(option)
                        }
                    }
                    .pickerStyle(.inline)
                }
            }
        }
    }

    private var content: some View {
        PaneContent(model: model)
    }

    private var title: String { model.paneTitle }

    private var subtitle: String {
        if case let .working(what) = model.phase { return what }
        if case let .failed(message) = model.phase { return message }
        switch model.pane {
        case .memos: return "\(model.memoCount) memos"
        case .tasks:
            let count = model.taskGroups.reduce(0) { $0 + $1.tasks.count }
            return "\(count) open across \(model.taskGroups.count) memos"
        case .review: return model.streak == 1 ? "1 day in a row" : "\(model.streak) days in a row"
        case .reminders:
            let count = model.reminders.count
            return count == 1 ? "1 waiting" : "\(count) waiting"
        case .templates:
            let daily = model.recurring.filter(\.enabled).count
            return daily == 0 ? "\(model.templates.count) saved" : "\(model.templates.count) saved, \(daily) daily"
        case .shortcuts:
            return model.shortcuts.count == 1 ? "1 shortcut" : "\(model.shortcuts.count) shortcuts"
        case .tags:
            return model.tags.count == 1 ? "1 tag" : "\(model.tags.count) tags"
        case .notifications:
            return model.unreadNotifications == 0 ? "Nothing unread" : "\(model.unreadNotifications) unread"
        case .profile, .stats, .tokens, .webhooks, .adminUsers, .adminInstance:
            return "From the server"
        case .data:
            return "Export, import, backup and restore"
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

