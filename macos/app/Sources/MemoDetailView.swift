import SwiftUI
import Shared

/// One memo, in full.
struct MemoDetailView: View {
    let detail: MemoDetail
    @ObservedObject var model: SessionModel
    var edit: () -> Void = {}
    @Environment(\.colorScheme) private var scheme

    private var memo: MemoRow { detail.row }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header

                if memo.locked {
                    lockedNotice
                } else {
                    MarkdownView(text: memo.body)
                        .frame(maxWidth: Theme.readingWidth, alignment: .leading)
                }

                if !memo.tags.isEmpty {
                    FlowTags(tags: memo.tags)
                }

                Divider().padding(.top, 4)
                footer
            }
            .padding(.horizontal, 40)
            .padding(.top, 32)
            .padding(.bottom, 48)
            .frame(maxWidth: Theme.readingWidth + 80, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .background(Theme.canvas)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                if let tint = Color.memoTint(memo.colourHex, isDark: scheme == .dark) {
                    Circle().fill(tint).frame(width: 10, height: 10)
                        .overlay(Circle().strokeBorder(Theme.hairline))
                }
                Text(memo.locked ? "Locked memo" : memo.title)
                    .font(Type.title)
                    .foregroundStyle(Theme.ink)
                    .textSelection(.enabled)
                Spacer(minLength: 8)
                MemoBadges(memo: memo)
                Menu {
                    Button("Edit", systemImage: "pencil", action: edit)
                        .disabled(memo.locked)
                    Button(memo.pinned ? "Unpin" : "Pin", systemImage: memo.pinned ? "pin.slash" : "pin") {
                        Task { await model.setPinned(memo.localId, !memo.pinned) }
                    }
                    Divider()
                    Button("Delete", systemImage: "trash", role: .destructive) {
                        Task { await model.delete(memo.localId) }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .menuStyle(.borderlessButton)
                .frame(width: 22)
            }
            Text(memo.dateLabel)
                .font(Type.rowMeta)
                .foregroundStyle(Theme.inkSoft)
        }
    }

    private var lockedNotice: some View {
        HStack(spacing: 10) {
            Image(systemName: "lock.fill").foregroundStyle(Theme.inkSoft)
            VStack(alignment: .leading, spacing: 2) {
                Text("This memo is encrypted").font(.system(size: 14, weight: .medium))
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
        HStack(spacing: 26) {
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
            Text(value).font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
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
