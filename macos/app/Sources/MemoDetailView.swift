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

                if memo.locked, let opened = model.revealed[memo.localId] {
                    revealedBanner
                    MarkdownView(text: opened)
                        .frame(maxWidth: Theme.readingWidth, alignment: .leading)
                } else if memo.locked {
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
                    if memo.locked {
                        Button("Show", systemImage: "eye") {
                            Task { await model.reveal(memo.localId) }
                        }
                        Button("Remove encryption", systemImage: "lock.open") {
                            Task { await model.unlockForGood(memo.localId) }
                        }
                    } else {
                        Button("Encrypt", systemImage: "lock") {
                            Task { await model.lock(memo.localId) }
                        }
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
        HStack(spacing: 12) {
            Image(systemName: "lock.fill").font(.system(size: 16)).foregroundStyle(Theme.inkSoft)
            VStack(alignment: .leading, spacing: 3) {
                Text("This memo is encrypted").font(.system(size: 14, weight: .medium))
                Text("Its text never reaches the server. Open it with your memo password.")
                    .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
            }
            Spacer(minLength: 12)
            Button("Show") { Task { await model.reveal(memo.localId) } }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
    }

    /// A reminder that what is on screen is not what is stored.
    private var revealedBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "lock.open.fill").font(.system(size: 11))
            Text("Shown with your password. Still encrypted on the server.")
                .font(Type.rowMeta)
            Spacer()
            Button("Hide") { model.revealed.removeValue(forKey: memo.localId) }
                .buttonStyle(.link)
                .font(Type.rowMeta)
        }
        .foregroundStyle(Theme.inkSoft)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Theme.accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
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
