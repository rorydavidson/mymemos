import SwiftUI
import Shared

/// A tag, as it appears on a card and in the sidebar.
struct TagChip: View {
    let tag: String
    var selected: Bool = false

    var body: some View {
        Text("#\(tag)")
            .font(Type.rowMeta)
            .foregroundStyle(selected ? Color.white : Theme.accent)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(
                Capsule().fill(selected ? Theme.accent : Theme.accent.opacity(0.12))
            )
    }
}

/// Pin, lock, tasks, attachments: the small facts about a memo that fit on one line.
struct MemoBadges: View {
    let memo: MemoRow

    var body: some View {
        HStack(spacing: 8) {
            if memo.pinned {
                Image(systemName: "pin.fill").foregroundStyle(.orange)
            }
            if memo.locked {
                Image(systemName: "lock.fill")
            }
            if memo.hasTasks {
                Image(systemName: memo.hasOpenTasks ? "checklist" : "checklist.checked")
                    .foregroundStyle(memo.hasOpenTasks ? Theme.accent : Theme.inkSoft)
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
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 32, weight: .light))
                .foregroundStyle(Theme.inkSoft.opacity(0.6))
            Text(title).font(.headline).foregroundStyle(Theme.ink)
            if let detail {
                Text(detail)
                    .font(.callout)
                    .foregroundStyle(Theme.inkSoft)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 280)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(40)
    }
}
