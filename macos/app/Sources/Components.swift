import SwiftUI
import Shared

/// A tag, as it appears on a card and in the sidebar.
struct TagChip: View {
    let tag: String
    var selected: Bool = false

    var body: some View {
        Text("#\(tag)")
            .font(Type.rowMeta)
            .foregroundStyle(selected ? Theme.card : Theme.onAccentSoft)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(
                Capsule().fill(selected ? Theme.accent : Theme.accentSoft)
            )
    }
}

/// Pin, lock, tasks, attachments: the small facts about a memo that fit on one line.
struct MemoBadges: View {
    let memo: MemoRow

    var body: some View {
        HStack(spacing: 8) {
            if memo.pinned {
                Image(systemName: "pin.fill").foregroundStyle(Theme.warm)
            }
            if memo.locked {
                Image(systemName: "lock.fill")
            }
            if memo.hasTasks {
                Image(systemName: memo.hasOpenTasks ? "checklist" : "checklist.checked")
                    .foregroundStyle(memo.hasOpenTasks ? Theme.accent : Theme.inkSoft.opacity(0.7))
            }
            if memo.attachmentCount > 0 {
                HStack(spacing: 2) {
                    Image(systemName: "paperclip")
                    Text("\(memo.attachmentCount)")
                }
            }
        }
        .font(Type.rowMeta)
        .foregroundStyle(Theme.inkSoft)
    }
}

/// The states a pane can be in when it has nothing to show, which is most of an app's polish.
struct EmptyState: View {
    let icon: String
    let title: String
    var detail: String?

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 30, weight: .light))
                .foregroundStyle(Theme.inkSoft.opacity(0.5))
            Text(title).font(Type.heading3).foregroundStyle(Theme.ink)
            if let detail {
                Text(detail)
                    .font(Type.rowBody)
                    .foregroundStyle(Theme.inkSoft)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 280)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(40)
    }
}
