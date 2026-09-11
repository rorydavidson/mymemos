import SwiftUI
import Shared

/// The iPad's sidebar: the same rows the Mac's has, drawn with the platform's own list so
/// that it collapses, swipes and sizes the way an iPad sidebar is expected to.
struct LibrarySidebar: View {
    @ObservedObject var model: SessionModel

    private enum Row: Hashable {
        case pane(SessionModel.Pane)
        case tag(String)
    }

    var body: some View {
        List(selection: selection) {
            Section("Library") {
                row("All memos", "tray.full", count: model.memoCount, .pane(.memos))
                row("Tasks", "checklist", count: model.openTaskCount, .pane(.tasks))
                row("Review", "calendar.badge.clock", count: nil, .pane(.review))
                row("Reminders", "bell", count: model.reminders.isEmpty ? nil : model.reminders.count, .pane(.reminders))
                row("Templates", "doc.on.doc", count: nil, .pane(.templates))
            }

            if !model.tags.isEmpty {
                Section("Tags") {
                    ForEach(model.tags, id: \.self) { tag in
                        row("#\(tag)", "number", count: model.count(forTag: tag), .tag(tag))
                    }
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
                if model.pane == .memos, let tag = model.activeTag { return .tag(tag) }
                return .pane(model.pane)
            },
            set: { chosen in
                switch chosen {
                case let .pane(pane):
                    model.pane = pane
                    if pane == .memos { model.activeTag = nil }
                case let .tag(tag):
                    model.pane = .memos
                    model.activeTag = tag
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
