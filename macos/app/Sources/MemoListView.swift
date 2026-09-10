import SwiftUI
import Shared

/// The timeline: memos under folding date headers, grouped by shared code so the Mac and the
/// phone agree about which day a memo belongs to.
///
/// Built on a ScrollView rather than a List on purpose. A List in a split view's content
/// column measures its rows before that column has settled on a width, so rows whose height
/// depends on how their text wraps come out clipped, and only correct themselves when
/// something forces another layout pass, such as dragging the splitter. A LazyVStack measures
/// from the content it is given, which is what these rows need. Selection and hover are then
/// ours to draw, which they already were.
struct MemoListView: View {
    @ObservedObject var model: SessionModel
    @Binding var selection: String?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 4, pinnedViews: [.sectionHeaders]) {
                ForEach(model.sections, id: \.key) { section in
                    Section {
                        if !model.collapsed.contains(section.key) {
                            ForEach(section.memos, id: \.localId) { memo in
                                MemoRowView(memo: memo, selected: selection == memo.localId)
                                    .onTapGesture { selection = memo.localId }
                                    .padding(.horizontal, 10)
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
            .padding(.vertical, 10)
        }
        .scrollContentBackground(.hidden)
        .background(Theme.canvas)
        .overlay(alignment: .center) {
            if model.sections.isEmpty && !model.isBusy {
                EmptyState(
                    icon: model.query.isEmpty ? "tray" : "magnifyingglass",
                    title: model.query.isEmpty ? "No memos yet" : "Nothing found",
                    detail: model.query.isEmpty
                        ? "Press ⌘N to write the first one."
                        : "No memo matches “\(model.query)”."
                )
            }
        }
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
                Image(systemName: "chevron.down")
                    .font(.system(size: 9, weight: .bold))
                    .rotationEffect(.degrees(collapsed ? -90 : 0))
                Text(label.uppercased())
                    .font(Type.sectionHeader)
                    .tracking(0.8)
                Text("\(count)")
                    .font(Type.rowMeta)
                    .monospacedDigit()
                    .foregroundStyle(Theme.inkSoft.opacity(0.7))
                Spacer()
            }
            .foregroundStyle(Theme.inkSoft)
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .background(Theme.canvas)
    }
}

/// One memo in the list. Title, a taste of the body, then the facts.
struct MemoRowView: View {
    let memo: MemoRow
    var selected = false

    @Environment(\.colorScheme) private var scheme
    @State private var hovering = false

    var body: some View {
        HStack(spacing: 0) {
            if let tint = Color.memoTint(memo.colourHex, isDark: scheme == .dark) {
                Rectangle().fill(tint.opacity(0.9)).frame(width: 4)
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(memo.locked ? "Locked memo" : memo.title)
                        .font(Type.rowTitle)
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    Text(memo.timeLabel)
                        .font(Type.rowMeta)
                        .monospacedDigit()
                        .foregroundStyle(Theme.inkSoft.opacity(0.8))
                }

                if !memo.locked, !bodyPreview.isEmpty {
                    Text(bodyPreview)
                        .font(Type.rowBody)
                        .foregroundStyle(Theme.inkSoft)
                        .lineSpacing(2.5)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if !memo.tags.isEmpty || hasBadges {
                    HStack(spacing: 6) {
                        ForEach(memo.tags.prefix(3), id: \.self) { TagChip(tag: $0) }
                        if memo.tags.count > 3 {
                            Text("+\(memo.tags.count - 3)")
                                .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                        }
                        Spacer(minLength: 0)
                        MemoBadges(memo: memo)
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        // The row's height comes from its content rather than from whatever the list guessed.
        .fixedSize(horizontal: false, vertical: true)
        .background(
            RoundedRectangle(cornerRadius: Theme.cardRadius)
                .fill(Color.memoTint(memo.colourHex, isDark: scheme == .dark) ?? Theme.card)
        )
        .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cardRadius)
                .strokeBorder(selected ? Theme.accent : Theme.hairline.opacity(0.8),
                              lineWidth: selected ? 1.5 : 0.5)
        )
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cardRadius)
                .fill(hovering && !selected ? Theme.ink.opacity(0.03) : .clear)
        )
        .contentShape(Rectangle())
        .onHover { hovering = $0 }
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
