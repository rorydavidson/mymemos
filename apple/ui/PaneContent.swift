import SwiftUI
import Shared

/// The content column for a pane. The Mac's window and the iPad's split view both use it;
/// the phone reaches the same screens through its menu instead.
struct PaneContent: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        switch model.pane {
        case .memos:
            MemoListView(model: model, selection: $model.selection)
        case .tasks:
            TasksView(model: model) { model.open($0) }
        case .review:
            ReviewView(model: model) { model.open($0) }
        case .reminders:
            RemindersView(model: model) { model.open($0) }
        case .templates:
            TemplatesView(model: model)
        case .shortcuts:
            ShortcutsView(model: model)
        case .tags:
            TagsView(model: model) { tag in
                model.activeTag = tag
                model.showArchived = false
                model.activeShortcut = nil
                model.pane = .memos
            }
        case .notifications:
            NotificationsView(model: model)
        case .profile:
            ProfileView(model: model)
        case .stats:
            StatsView(model: model)
        case .tokens:
            TokensView(model: model)
        case .webhooks:
            WebhooksView(model: model)
        case .adminUsers:
            AdminUsersView(model: model)
        case .adminInstance:
            AdminInstanceView(model: model)
        case .data:
            DataView(model: model)
        }
    }
}
