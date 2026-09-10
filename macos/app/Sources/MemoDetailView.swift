import SwiftUI
import Shared

/// One memo, in full.
struct MemoDetailView: View {
    let detail: MemoDetail
    @Environment(\.colorScheme) private var scheme

    private var memo: MemoRow { detail.row }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header

                if memo.locked {
                    lockedNotice
                } else {
                    MarkdownView(text: memo.body)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if !memo.tags.isEmpty {
                    FlowTags(tags: memo.tags)
                }

                Divider().padding(.top, 4)
                footer
            }
            .padding(28)
            .frame(maxWidth: 760, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .background(Theme.canvas)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                if let tint = Color.memoTint(memo.colourHex, isDark: scheme == .dark) {
                    Circle().fill(tint).frame(width: 10, height: 10)
                        .overlay(Circle().strokeBorder(Theme.hairline))
                }
                Text(memo.locked ? "Locked memo" : memo.title)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                MemoBadges(memo: memo)
            }
            Text(memo.dateLabel)
                .font(.caption)
                .foregroundStyle(Theme.inkSoft)
        }
    }

    private var lockedNotice: some View {
        HStack(spacing: 10) {
            Image(systemName: "lock.fill").foregroundStyle(Theme.inkSoft)
            VStack(alignment: .leading, spacing: 2) {
                Text("This memo is encrypted").font(.callout.weight(.medium))
                Text("Its text never reaches the server. Unlocking it here is not built yet.")
                    .font(.caption).foregroundStyle(Theme.inkSoft)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
    }

    private var footer: some View {
        HStack(spacing: 20) {
            fact("Created", detail.created)
            fact("Last changed", detail.updated)
            fact("Visibility", detail.visibility)
            if !memo.locked { fact("Words", "\(detail.wordCount)") }
            Spacer()
        }
    }

    private func fact(_ name: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name.uppercased())
                .font(.system(size: 9, weight: .semibold))
                .tracking(0.5)
                .foregroundStyle(Theme.inkSoft.opacity(0.8))
            Text(value).font(.caption).foregroundStyle(Theme.inkSoft)
        }
    }
}

/// Tags wrap rather than scroll, because a memo can carry a lot of them.
struct FlowTags: View {
    let tags: [String]

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 60, maximum: 200), spacing: 6, alignment: .leading)],
                  alignment: .leading, spacing: 6) {
            ForEach(tags, id: \.self) { TagChip(tag: $0) }
        }
    }
}
