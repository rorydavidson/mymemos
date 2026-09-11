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
            LazyVStack(alignment: .leading, spacing: model.compactList ? 1 : 4, pinnedViews: [.sectionHeaders]) {
                ForEach(model.sections, id: \.key) { section in
                    Section {
                        if !model.collapsed.contains(section.key) {
                            ForEach(section.memos, id: \.localId) { memo in
                                Group {
                                    if model.compactList {
                                        CompactMemoRowView(memo: memo, selected: selection == memo.localId)
                                    } else {
                                        MemoRowView(memo: memo, selected: selection == memo.localId)
                                    }
                                }
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
                        ? Hint.firstMemo
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

/// One memo as a single line: its title, the time it was written, and the badges that say
/// something a title cannot. For scanning a long timeline rather than reading it, and the
/// same idea as the phone's compact rows.
struct CompactMemoRowView: View {
    let memo: MemoRow
    var selected = false

    @Environment(\.colorScheme) private var scheme
    @State private var hovering = false

    var body: some View {
        HStack(spacing: 8) {
            // The colour a card shows as a whole tint has to survive here as something, so it
            // becomes the bar down the side. Without it a coloured memo is indistinguishable.
            Rectangle()
                .fill(Color.memoTint(memo.colourHex, isDark: scheme == .dark)?.opacity(0.9) ?? .clear)
                .frame(width: 3)

            if memo.pinned {
                Image(systemName: "pin.fill")
                    .font(.system(size: 10))
                    .foregroundStyle(Theme.accent)
            }
            if memo.locked {
                Image(systemName: "lock.fill")
                    .font(.system(size: 10))
                    .foregroundStyle(Theme.accent)
            }

            Text(title)
                .font(Type.rowBody)
                .foregroundStyle(memo.locked ? Theme.inkSoft : Theme.ink)
                .lineLimit(1)
                .truncationMode(.tail)

            Spacer(minLength: 8)

            if memo.hasOpenTasks {
                Image(systemName: "checklist")
                    .font(.system(size: 10))
                    .foregroundStyle(Theme.inkSoft)
            }
            if memo.attachmentCount > 0 {
                Image(systemName: "paperclip")
                    .font(.system(size: 10))
                    .foregroundStyle(Theme.inkSoft)
            }

            Text(memo.timeLabel)
                .font(Type.rowMeta)
                .monospacedDigit()
                .foregroundStyle(Theme.inkSoft.opacity(0.8))
        }
        .padding(.trailing, 12)
        .padding(.vertical, 6)
        .frame(height: 28)
        .background(
            RoundedRectangle(cornerRadius: 6)
                .fill(selected ? Theme.accent.opacity(0.16) : (hovering ? Theme.ink.opacity(0.04) : .clear))
        )
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .contentShape(Rectangle())
        .onHover { hovering = $0 }
    }

    /// A locked memo's body is ciphertext, so there is no title to be had from it.
    private var title: String {
        if memo.locked { return "Locked memo" }
        return memo.title.isEmpty ? "Untitled" : memo.title
    }
}
