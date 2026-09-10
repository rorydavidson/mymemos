import SwiftUI
import Shared

/// The sidebar.
///
/// Deliberately not a stack of plain `Label`s in a `.sidebar` list, which is what gives an app
/// that untouched look: the rows carry counts, the selected one is a filled capsule in the
/// app's own green rather than the system blue, and the sections are set in the app's type
/// rather than the platform's.
struct Sidebar: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 2) {
                    header("Library")

                    row("All memos", "tray.full", count: model.memoCount,
                        selected: model.pane == .memos && model.activeTag == nil) {
                        model.pane = .memos
                        model.activeTag = nil
                    }
                    row("Tasks", "checklist", count: model.openTaskCount,
                        selected: model.pane == .tasks) { model.pane = .tasks }
                    row("Review", "calendar.badge.clock", count: nil,
                        selected: model.pane == .review) { model.pane = .review }
                    row("Reminders", "bell", count: model.reminders.isEmpty ? nil : model.reminders.count,
                        selected: model.pane == .reminders) { model.pane = .reminders }
                    row("Templates", "doc.on.doc", count: nil,
                        selected: model.pane == .templates) { model.pane = .templates }

                    if !model.tags.isEmpty {
                        header("Tags").padding(.top, 14)
                        ForEach(model.tags, id: \.self) { tag in
                            row("#\(tag)", "number", count: model.count(forTag: tag),
                                selected: model.activeTag == tag) {
                                model.activeTag = model.activeTag == tag ? nil : tag
                                model.pane = .memos
                            }
                        }
                    }
                }
                .padding(.horizontal, 8)
                .padding(.top, 10)
                .padding(.bottom, 14)
            }
            .scrollContentBackground(.hidden)

            account
        }
        .background(Theme.canvas.opacity(0.6))
    }

    private func header(_ text: String) -> some View {
        Text(text.uppercased())
            .font(Type.label)
            .tracking(0.8)
            .foregroundStyle(Theme.inkSoft.opacity(0.7))
            .padding(.horizontal, 8)
            .padding(.bottom, 4)
    }

    private func row(
        _ title: String,
        _ symbol: String,
        count: Int?,
        selected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        SidebarRow(title: title, symbol: symbol, count: count, selected: selected, action: action)
    }

    private var account: some View {
        VStack(spacing: 0) {
            Divider().overlay(Theme.hairline)
            HStack(spacing: 9) {
                AvatarView(image: model.avatar, initials: initials)
                    .frame(width: 26, height: 26)

                VStack(alignment: .leading, spacing: 0) {
                    Text(model.displayName.isEmpty ? "Not signed in" : model.displayName)
                        .font(Type.rowTitle).foregroundStyle(Theme.ink).lineLimit(1)
                    Text(model.serverVersion.isEmpty ? "—" : "Memos \(model.serverVersion)")
                        .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                }

                Spacer(minLength: 4)

                if model.passwordRemembered {
                    FooterButton(symbol: "lock.rotation",
                                 help: "Forget the memo password on this Mac") {
                        model.forgetPassword()
                    }
                }

                SettingsLink {
                    Image(systemName: "gearshape")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.inkSoft)
                        .frame(width: 24, height: 22)
                }
                .buttonStyle(.plain)
                .help("Settings (⌘,)")
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
        }
    }

    private var initials: String {
        let parts = model.displayName.split(separator: " ").prefix(2)
        let letters = parts.compactMap { $0.first }.map(String.init).joined()
        return letters.isEmpty ? "?" : letters.uppercased()
    }
}

/// One sidebar row, with its own hover and selection rather than the system's.
private struct SidebarRow: View {
    let title: String
    let symbol: String
    let count: Int?
    let selected: Bool
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: symbol)
                    .font(.system(size: 12))
                    .frame(width: 16)
                Text(title)
                    .font(Type.sidebar)
                    .lineLimit(1)
                Spacer(minLength: 6)
                if let count, count > 0 {
                    Text("\(count)")
                        .font(Type.rowMeta)
                        .monospacedDigit()
                        .foregroundStyle(selected ? Theme.onAccentSoft.opacity(0.7) : Theme.inkSoft.opacity(0.75))
                }
            }
            .foregroundStyle(selected ? Theme.onAccentSoft : Theme.ink)
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 7)
                    .fill(selected ? Theme.accentSoft : (hovering ? Theme.hairline.opacity(0.55) : .clear))
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
    }
}

/// The account's picture, or its initials when there is none.
private struct AvatarView: View {
    let image: NSImage?
    let initials: String

    var body: some View {
        ZStack {
            Circle().fill(Theme.accentSoft)
            if let image {
                Image(nsImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .clipShape(Circle())
            } else {
                Text(initials)
                    .font(Type.rowMeta)
                    .foregroundStyle(Theme.onAccentSoft)
            }
        }
        .overlay(Circle().strokeBorder(Theme.hairline, lineWidth: 0.5))
    }
}

/// A quiet button for the sidebar's footer.
private struct FooterButton: View {
    let symbol: String
    let help: String
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 12))
                .foregroundStyle(Theme.inkSoft)
                .frame(width: 24, height: 22)
                .background(
                    RoundedRectangle(cornerRadius: 5)
                        .fill(hovering ? Theme.hairline.opacity(0.7) : .clear)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .help(help)
    }
}
