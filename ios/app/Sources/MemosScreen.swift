import SwiftUI
import Shared

/// The memos tab on a phone: the timeline, search, and the menu that reaches everything the
/// Mac keeps in its sidebar.
/// The phone's navigation path, held outside the view so the library screens can be reached
/// from elsewhere (the tour driver, and later a widget or a notification).
@MainActor
final class PhoneNavigation: ObservableObject {
    static let shared = PhoneNavigation()
    @Published var path: [MemosScreen.Library] = []
}

struct MemosScreen: View {
    @ObservedObject var model: SessionModel
    @ObservedObject private var nav = PhoneNavigation.shared

    enum Library: String, Hashable {
        case reminders, templates, settings, shortcuts, tags, notifications
        case profile, tokens, webhooks, stats, adminUsers, adminInstance
    }

    private var title: String {
        if let shortcut = model.activeShortcut { return shortcut.title }
        if model.showArchived { return "Archive" }
        return model.activeTag.map { "#\($0)" } ?? "Memos"
    }

    var body: some View {
        NavigationStack(path: $nav.path) {
            MemoListView(model: model, selection: $model.selection)
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.large)
                .searchable(text: $model.query, prompt: "Search memos")
                .refreshable { await model.sync() }
                .toolbar { MemosToolbar(model: model) }
                .navigationDestination(item: $model.selection) { id in
                    DetailScreen(model: model, localId: id)
                }
                .navigationDestination(for: Library.self) { screen in
                    switch screen {
                    case .reminders:
                        RemindersView(model: model) { model.open($0) }.navigationTitle("Reminders")
                    case .templates:
                        TemplatesView(model: model).navigationTitle("Templates")
                    case .settings:
                        SettingsView(model: model).navigationTitle("Settings")
                    case .shortcuts:
                        ShortcutsView(model: model).navigationTitle("Shortcuts")
                    case .tags:
                        TagsView(model: model) { tag in model.activeTag = tag; model.showArchived = false }.navigationTitle("Tags")
                    case .notifications:
                        NotificationsView(model: model).navigationTitle("Notifications")
                    case .profile:
                        ProfileView(model: model).navigationTitle("Profile")
                    case .tokens:
                        TokensView(model: model).navigationTitle("Access tokens")
                    case .webhooks:
                        WebhooksView(model: model).navigationTitle("Webhooks")
                    case .stats:
                        StatsView(model: model).navigationTitle("Statistics")
                    case .adminUsers:
                        AdminUsersView(model: model).navigationTitle("Users")
                    case .adminInstance:
                        AdminInstanceView(model: model).navigationTitle("Instance")
                    }
                }
        }
        .onChange(of: model.query) { _, _ in Task { await model.reload() } }
        .onChange(of: model.activeTag) { _, _ in Task { await model.reload() } }
    }
}

/// The timeline's toolbar: sync, a new memo, and the library menu.
struct MemosToolbar: ToolbarContent {
    @ObservedObject var model: SessionModel
    @State private var showingSync = false

    var body: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Menu {
                Section {
                    NavigationLink(value: MemosScreen.Library.reminders) {
                        Label("Reminders", systemImage: "bell")
                    }
                    NavigationLink(value: MemosScreen.Library.templates) {
                        Label("Templates", systemImage: "doc.on.doc")
                    }
                    NavigationLink(value: MemosScreen.Library.shortcuts) {
                        Label(model.activeShortcut.map { "Shortcut: \($0.title)" } ?? "Shortcuts", systemImage: "line.3.horizontal.decrease.circle")
                    }
                    NavigationLink(value: MemosScreen.Library.tags) {
                        Label("Tags", systemImage: "number")
                    }
                    NavigationLink(value: MemosScreen.Library.notifications) {
                        Label(model.unreadNotifications > 0 ? "Notifications (\(model.unreadNotifications))" : "Notifications", systemImage: "bell.badge")
                    }
                }

                if !model.tags.isEmpty {
                    Menu {
                        Button {
                            model.activeTag = nil
                        } label: {
                            Label("All memos", systemImage: model.activeTag == nil ? "checkmark" : "")
                        }
                        ForEach(model.tags, id: \.self) { tag in
                            Button {
                                model.activeTag = model.activeTag == tag ? nil : tag
                            } label: {
                                Label("#\(tag)", systemImage: model.activeTag == tag ? "checkmark" : "number")
                            }
                        }
                    } label: {
                        Label("Tags", systemImage: "number")
                    }
                }

                Section {
                    Toggle(isOn: Binding(
                        get: { model.showArchived },
                        set: { on in model.showArchived = on; model.activeShortcut = nil; Task { await model.reload() } }
                    )) {
                        Label("Archive", systemImage: "archivebox")
                    }
                    Toggle(isOn: Binding(
                        get: { model.compactList },
                        set: { enabled in Task { await model.setCompactList(enabled) } }
                    )) {
                        Label("Compact list", systemImage: "list.bullet")
                    }
                    Toggle(isOn: Binding(
                        get: { model.sortByModified },
                        set: { enabled in Task { await model.setSortByModified(enabled) } }
                    )) {
                        Label("Sort by last changed", systemImage: "arrow.up.arrow.down")
                    }
                }

                Section {
                    Button { showingSync = true } label: {
                        Label("Sync status", systemImage: "arrow.triangle.2.circlepath")
                    }
                    NavigationLink(value: MemosScreen.Library.settings) {
                        Label("Settings", systemImage: "gearshape")
                    }
                }
            } label: {
                Image(systemName: "line.3.horizontal")
                    .overlay(alignment: .topTrailing) {
                        if (model.syncStatus?.failed ?? 0) > 0 || (model.syncStatus?.authExpired ?? false) || model.unreadNotifications > 0 {
                            Circle().fill(Theme.danger).frame(width: 7, height: 7).offset(x: 3, y: -3)
                        }
                    }
            }
            .sheet(isPresented: $showingSync) {
                SyncStatusView(model: model).presentationDetents([.medium, .large])
            }
        }

        ToolbarItem(placement: .topBarTrailing) {
            SyncStatusButton(model: model)
        }

        ToolbarItem(placement: .topBarTrailing) {
            Button { model.newMemo() } label: {
                Image(systemName: "square.and.pencil")
            }
        }
    }
}
