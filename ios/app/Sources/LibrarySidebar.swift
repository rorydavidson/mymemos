import SwiftUI
import Shared

/// The iPad's sidebar: the same rows the Mac's has, drawn with the platform's own list so
/// that it collapses, swipes and sizes the way an iPad sidebar is expected to.
struct LibrarySidebar: View {
    @ObservedObject var model: SessionModel

    private enum Row: Hashable {
        case pane(SessionModel.Pane)
        case tag(String)
        case archive
        case shortcut(String)
    }

    var body: some View {
        List(selection: selection) {
            Section("Library") {
                row("All memos", "tray.full", count: model.memoCount, .pane(.memos))
                row("Archive", "archivebox", count: nil, .archive)
                row("Tasks", "checklist", count: model.openTaskCount, .pane(.tasks))
                row("Review", "calendar.badge.clock", count: nil, .pane(.review))
                row("Reminders", "bell", count: model.reminders.isEmpty ? nil : model.reminders.count, .pane(.reminders))
                row("Templates", "doc.on.doc", count: nil, .pane(.templates))
                row("Notifications", "bell.badge", count: model.unreadNotifications == 0 ? nil : model.unreadNotifications, .pane(.notifications))
            }

            Section("Shortcuts") {
                ForEach(model.shortcuts, id: \.name) { shortcut in
                    row(shortcut.title, "line.3.horizontal.decrease.circle", count: nil, .shortcut(shortcut.name))
                }
                row(model.shortcuts.isEmpty ? "Add a shortcut" : "Manage shortcuts", "slider.horizontal.3", count: nil, .pane(.shortcuts))
            }

            if !model.tags.isEmpty {
                Section("Tags") {
                    ForEach(model.tags, id: \.self) { tag in
                        row("#\(tag)", "number", count: model.count(forTag: tag), .tag(tag))
                    }
                    row("Tag styles", "paintpalette", count: nil, .pane(.tags))
                }
            }

            Section("Account") {
                row("Profile", "person.crop.circle", count: nil, .pane(.profile))
                row("Statistics", "chart.bar", count: nil, .pane(.stats))
                row("Access tokens", "key", count: nil, .pane(.tokens))
                row("Webhooks", "arrow.up.forward.app", count: nil, .pane(.webhooks))
                row("Your data", "externaldrive", count: nil, .pane(.data))
            }

            if model.isAdmin {
                Section("Administration") {
                    row("Users", "person.2", count: nil, .pane(.adminUsers))
                    row("Instance", "server.rack", count: nil, .pane(.adminInstance))
                }
            }
        }
        .listStyle(.sidebar)
        .navigationTitle("MyMemos")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                NavigationLink {
                    SettingsView(model: model).navigationTitle("Settings")
                } label: {
                    Image(systemName: "gearshape")
                }
            }
        }
    }

    /// The pane and the tag filter are one selection: choosing a tag shows the memos pane.
    private var selection: Binding<Row?> {
        Binding(
            get: {
                if model.pane == .memos {
                    if let shortcut = model.activeShortcut { return .shortcut(shortcut.name) }
                    if model.showArchived { return .archive }
                    if let tag = model.activeTag { return .tag(tag) }
                }
                return .pane(model.pane)
            },
            set: { chosen in
                switch chosen {
                case let .pane(pane):
                    if pane == .memos { Task { await model.showAllMemos() } } else { model.pane = pane }
                case let .tag(tag):
                    model.pane = .memos
                    model.showArchived = false
                    model.activeShortcut = nil
                    model.activeTag = tag
                case .archive:
                    Task { await model.showArchive(true) }
                case let .shortcut(name):
                    if let shortcut = model.shortcuts.first(where: { $0.name == name }) { Task { await model.showShortcut(shortcut) } }
                case nil:
                    break
                }
            }
        )
    }

    private func row(_ title: String, _ symbol: String, count: Int?, _ value: Row) -> some View {
        HStack {
            Label(title, systemImage: symbol)
            Spacer()
            if let count, count > 0 {
                Text("\(count)")
                    .font(Type.rowMeta)
                    .monospacedDigit()
                    .foregroundStyle(Theme.inkSoft)
            }
        }
        .tag(value)
    }
}
