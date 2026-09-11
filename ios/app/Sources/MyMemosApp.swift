import SwiftUI
import Shared

// The iOS client's shell. The session model and nearly every view live in apple/ui and are
// shared with the Mac; what is here is the phone's tab bar, the iPad's split view, and the
// text view the editor sits on.

@main
struct MyMemosApp: App {
    @StateObject private var model = SessionModel()


    var body: some Scene {
        WindowGroup {
            RootView(model: model)
                .preferredColorScheme(model.appearance.colorScheme)
                .tint(Theme.accent)
        }
    }
}

struct RootView: View {
    @ObservedObject var model: SessionModel
    @Environment(\.horizontalSizeClass) private var sizeClass

    var body: some View {
        Group {
            if model.phase == .signedOut {
                SignInView(model: model)
            } else if sizeClass == .regular {
                SplitShell(model: model)
            } else {
                TabShell(model: model)
            }
        }
        .task {
            await model.restore()
            #if TESTHOOKS
            await TestHooks.run(model)
            #endif
        }
        .sheet(item: $model.editing) { target in
            EditorView(model: model, editing: target.localId, initialText: target.initialText)
        }
        .sheet(item: $model.passwordRequest) { request in
            PasswordSheet(model: model, request: request)
                .presentationDetents([.medium])
        }
        .sheet(item: Binding(
            get: { model.settingReminderFor.map(ReminderTarget.init) },
            set: { if $0 == nil { model.settingReminderFor = nil } }
        )) { target in
            ReminderSheet(model: model, memoLocalId: target.id)
                .presentationDetents([.medium])
        }        .alert("MyMemos", isPresented: Binding(get: { model.notice != nil }, set: { if !$0 { model.notice = nil } })) {
            Button("OK") { model.notice = nil }
        } message: {
            Text(model.notice ?? "")
        }
    }
}

// MARK: - iPhone

/// The phone's three tabs, the same three the Android app's bottom bar has. Everything else
/// is reached from the memos tab's menu.
struct TabShell: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        TabView(selection: $model.pane) {
            MemosScreen(model: model)
                .tabItem { Label("Memos", systemImage: "tray.full") }
                .tag(SessionModel.Pane.memos)

            NavigationStack {
                TasksView(model: model) { model.open($0) }
                    .navigationTitle("Tasks")
                    .navigationDestination(item: $model.selection) { id in
                        DetailScreen(model: model, localId: id)
                    }
            }
            .tabItem { Label("Tasks", systemImage: "checklist") }
            .tag(SessionModel.Pane.tasks)

            NavigationStack {
                ReviewView(model: model) { model.open($0) }
                    .navigationTitle("Review")
                    .navigationDestination(item: $model.selection) { id in
                        DetailScreen(model: model, localId: id)
                    }
            }
            .tabItem { Label("Review", systemImage: "calendar.badge.clock") }
            .tag(SessionModel.Pane.review)
        }
    }
}

// MARK: - iPad

/// Three columns on an iPad, the way the Mac lays it out.
struct SplitShell: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        NavigationSplitView {
            LibrarySidebar(model: model)
        } content: {
            content
                .navigationTitle(title)
        } detail: {
            NavigationStack {
                DetailPane(model: model, selection: model.selection, edit: { model.editing = EditorTarget(localId: $0) })
            }
        }
        .onChange(of: model.query) { _, _ in Task { await model.reload() } }
        .onChange(of: model.activeTag) { _, _ in Task { await model.reload() } }
    }

    @ViewBuilder
    private var content: some View {
        switch model.pane {
        case .memos:
            MemoListView(model: model, selection: $model.selection)
                .searchable(text: $model.query, prompt: "Search memos")
                .refreshable { await model.sync() }
                .toolbar { MemosToolbar(model: model) }
        case .tasks:
            TasksView(model: model) { model.open($0) }
        case .review:
            ReviewView(model: model) { model.open($0) }
        case .reminders:
            RemindersView(model: model) { model.open($0) }
        case .templates:
            TemplatesView(model: model)
        }
    }

    private var title: String {
        switch model.pane {
        case .memos:
            if let shortcut = model.activeShortcut { return shortcut.title }
            if model.showArchived { return "Archive" }
            return model.activeTag.map { "#\($0)" } ?? "Memos"
        case .tasks: return "Tasks"
        case .review: return "Review"
        case .reminders: return "Reminders"
        case .templates: return "Templates"
        }
    }
}

/// One memo, pushed onto a phone's navigation stack.
struct DetailScreen: View {
    @ObservedObject var model: SessionModel
    let localId: String

    var body: some View {
        DetailPane(model: model, selection: localId, edit: { model.editing = EditorTarget(localId: $0) })
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { model.editing = EditorTarget(localId: localId) } label: {
                        Image(systemName: "pencil")
                    }
                    .disabled(model.memo(localId)?.locked == true)
                }
            }
    }
}
