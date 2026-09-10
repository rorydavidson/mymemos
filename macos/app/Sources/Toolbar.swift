import SwiftUI
import Shared

/// What the toolbar shows about syncing.
///
/// A bare refresh button says nothing about whether the app is up to date, which on an
/// offline-first app is the one thing worth knowing: everything on screen came from the local
/// database, so "when did that last agree with the server" is a real question.
struct SyncStatusButton: View {
    @ObservedObject var model: SessionModel
    @State private var hovering = false

    var body: some View {
        Button { Task { await model.sync() } } label: {
            HStack(spacing: 6) {
                icon
                if let label = statusLabel {
                    Text(label)
                        .font(Type.rowMeta)
                        .foregroundStyle(Theme.inkSoft)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                RoundedRectangle(cornerRadius: 7)
                    .fill(hovering && !model.isBusy ? Theme.hairline.opacity(0.6) : .clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(model.isBusy)
        .onHover { hovering = $0 }
        .help(helpText)
    }

    @ViewBuilder
    private var icon: some View {
        if model.isBusy {
            ProgressView().controlSize(.small).scaleEffect(0.7).frame(width: 14, height: 14)
        } else if case .failed = model.phase {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 11))
                .foregroundStyle(Theme.danger)
        } else {
            Image(systemName: "checkmark.icloud")
                .font(.system(size: 12))
                .foregroundStyle(Theme.accent)
        }
    }

    private var statusLabel: String? {
        if case .failed = model.phase { return "Sync failed" }
        if model.isBusy { return nil }
        guard let synced = model.lastSynced else { return nil }
        let seconds = Int(Date().timeIntervalSince(synced))
        if seconds < 60 { return "Just now" }
        if seconds < 3600 { return "\(seconds / 60)m ago" }
        return "\(seconds / 3600)h ago"
    }

    private var helpText: String {
        if case let .failed(message) = model.phase { return message }
        return "Sync with the server"
    }
}

/// A toolbar button that reads as part of this app rather than the platform's chrome.
struct ToolbarIcon: View {
    let symbol: String
    let help: String
    var disabled = false
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 13))
                .foregroundStyle(disabled ? Theme.inkSoft.opacity(0.4) : Theme.ink)
                .frame(width: 26, height: 22)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(hovering && !disabled ? Theme.hairline.opacity(0.6) : .clear)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .onHover { hovering = $0 }
        .help(help)
    }
}

/// A menu that looks like the icons beside it.
///
/// SwiftUI draws a disclosure chevron on a `Menu` by default, which next to a plain symbol
/// button reads as a different kind of control and, at toolbar sizes, sits on top of the icon
/// rather than beside it. Hiding the indicator and using the same frame as `ToolbarIcon` keeps
/// the row consistent.
struct ToolbarMenu<Content: View>: View {
    let symbol: String
    let help: String
    @ViewBuilder var content: () -> Content

    @State private var hovering = false

    var body: some View {
        Menu {
            content()
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 13))
                .foregroundStyle(Theme.ink)
        }
        .menuStyle(.borderlessButton)
        .menuIndicator(.hidden)
        .frame(width: 26, height: 22)
        .background(
            RoundedRectangle(cornerRadius: 6)
                .fill(hovering ? Theme.hairline.opacity(0.6) : .clear)
        )
        .onHover { hovering = $0 }
        .help(help)
    }
}
