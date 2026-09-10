import SwiftUI
import Shared

/// The timeline: memos under folding date headers, grouped by shared code so the Mac and the
/// phone agree about which day a memo belongs to.
struct MemoListView: View {
    @ObservedObject var model: SessionModel
    @Binding var selection: String?

    var body: some View {
        List(selection: $selection) {
            ForEach(model.sections, id: \.key) { section in
                Section {
                    if !model.collapsed.contains(section.key) {
                        ForEach(section.memos, id: \.localId) { memo in
                            MemoRowView(memo: memo)
                                .tag(memo.localId)
                                .listRowSeparator(.hidden)
                                .listRowInsets(EdgeInsets(top: 3, leading: 8, bottom: 3, trailing: 8))
                        }
                    }
                } header: {
                    SectionHeader(
                        label: section.label,
                        count: section.memos.count,
                        collapsed: model.collapsed.contains(section.key),
                        toggle: { model.toggle(section.key) }
                    )
                }
            }
        }
        .listStyle(.inset)
        .scrollContentBackground(.hidden)
        .background(Theme.canvas)
    }
}

private struct SectionHeader: View {
    let label: String
    let count: Int
    let collapsed: Bool
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            HStack(spacing: 6) {
                Image(systemName: "chevron.right")
                    .font(.caption2.weight(.semibold))
                    .rotationEffect(.degrees(collapsed ? 0 : 90))
                Text(label.uppercased())
                    .font(.caption.weight(.semibold))
                    .tracking(0.6)
                Text("\(count)")
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(Theme.inkSoft)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1)
                    .background(Capsule().fill(Theme.hairline))
                Spacer()
            }
            .foregroundStyle(Theme.inkSoft)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.vertical, 2)
    }
}

/// One memo in the list. Title, a taste of the body, then the facts.
struct MemoRowView: View {
    let memo: MemoRow
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        HStack(spacing: 0) {
            if let tint = Color.memoTint(memo.colourHex, isDark: scheme == .dark) {
                Rectangle().fill(tint).frame(width: 3)
            }
            VStack(alignment: .leading, spacing: Theme.rowSpacing) {
                HStack(alignment: .firstTextBaseline) {
                    Text(memo.locked ? "Locked memo" : memo.title)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    Text(memo.timeLabel)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(Theme.inkSoft)
                }

                if !memo.locked, !bodyPreview.isEmpty {
                    Text(bodyPreview)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.inkSoft)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if !memo.tags.isEmpty || hasBadges {
                    HStack(spacing: 6) {
                        ForEach(memo.tags.prefix(3), id: \.self) { TagChip(tag: $0) }
                        if memo.tags.count > 3 {
                            Text("+\(memo.tags.count - 3)").font(.caption2).foregroundStyle(Theme.inkSoft)
                        }
                        Spacer(minLength: 0)
                        MemoBadges(memo: memo)
                    }
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
        }
        .background(
            RoundedRectangle(cornerRadius: Theme.cardRadius)
                .fill(Color.memoTint(memo.colourHex, isDark: scheme == .dark) ?? Theme.card)
        )
        .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline)
        )
    }

    private var hasBadges: Bool {
        memo.pinned || memo.locked || memo.hasTasks || memo.attachmentCount > 0
    }

    /// The body without the line already used as the title, so the preview is not a repeat.
    private var bodyPreview: String {
        let lines = memo.body.components(separatedBy: "\n")
        let remainder = lines.drop { $0.trimmingCharacters(in: .whitespaces).isEmpty }.dropFirst()
        return remainder
            .joined(separator: " ")
            .replacingOccurrences(of: "#", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
