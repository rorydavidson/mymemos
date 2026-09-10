import SwiftUI
import Shared

/// Every unticked task across the account, grouped by the memo it lives in.
///
/// Ticking one edits that memo's text, which is the same thing the phone does: a task is a
/// line in a memo, not a separate record, and treating it as one is what keeps the two in step.
struct TasksView: View {
    @ObservedObject var model: SessionModel
    var openMemo: (String) -> Void

    var body: some View {
        Group {
            if model.taskGroups.isEmpty {
                EmptyState(
                    icon: "checklist",
                    title: "Nothing open",
                    detail: "Write \"- [ ] something @tomorrow\" in a memo and it will appear here."
                )
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        ForEach(model.taskGroups, id: \.memoLocalId) { group in
                            groupCard(group)
                        }
                    }
                    .padding(20)
                    .frame(maxWidth: Theme.readingWidth + 60, alignment: .leading)
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                }
            }
        }
        .background(Theme.canvas)
        .task { await model.loadTasks() }
    }

    private func groupCard(_ group: TaskGroup) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { openMemo(group.memoLocalId) } label: {
                HStack(spacing: 6) {
                    Text(group.memoTitle)
                        .font(Type.rowTitle)
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                    Image(systemName: "arrow.up.right")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(Theme.inkSoft)
                    Spacer()
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .help("Open this memo")
            .padding(.horizontal, 14)
            .padding(.vertical, 10)

            Divider()

            VStack(alignment: .leading, spacing: 0) {
                ForEach(group.tasks, id: \.lineIndex) { task in
                    TaskRowView(task: task) {
                        Task { await model.completeTask(group.memoLocalId, Int(task.lineIndex)) }
                    }
                    if task.lineIndex != group.tasks.last?.lineIndex {
                        Divider().padding(.leading, 38)
                    }
                }
            }
        }
        .background(Theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
    }
}

private struct TaskRowView: View {
    let task: TaskRow
    let complete: () -> Void

    @State private var hovering = false

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Button(action: complete) {
                Image(systemName: hovering ? "checkmark.square.fill" : "square")
                    .font(.system(size: 15))
                    .foregroundStyle(hovering ? Theme.accent : Theme.inkSoft)
            }
            .buttonStyle(.plain)
            .help("Tick this off")

            Text(task.text)
                .font(Type.body)
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 8)

            if let due = task.dueLabel {
                Text(due)
                    .font(Type.rowMeta.weight(.medium))
                    .foregroundStyle(task.overdue ? .white : Theme.accent)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(
                        Capsule().fill(task.overdue ? Color.red.opacity(0.85) : Theme.accent.opacity(0.12))
                    )
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .contentShape(Rectangle())
        .onHover { hovering = $0 }
    }
}
