import SwiftUI
import UniformTypeIdentifiers
import Shared

/// One memo, in full.
struct MemoDetailView: View {
    let detail: MemoDetail
    @ObservedObject var model: SessionModel
    var edit: () -> Void = {}
    @Environment(\.colorScheme) private var scheme
    @State private var attachments: [AttachmentRow] = []
    @State private var settingReminder = false
    @State private var pickingReference = false
    @State private var sharing = false
    @State private var confirmingDelete = false
    @State private var commentDraft = ""
    #if os(iOS)
    @State private var importing = false
    #endif

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
                    MarkdownView(text: detail.bodyBelowTitle) { line, checked in
                        Task { await model.toggleTask(memo.localId, line: line + Int(detail.bodyLineOffset), checked: checked) }
                    }
                    .frame(maxWidth: Theme.readingWidth, alignment: .leading)
                }

                if !memo.tags.isEmpty {
                    FlowTags(tags: memo.tags, styles: model.tagStyles)
                }

                if !attachments.isEmpty {
                    AttachmentStrip(model: model, memoLocalId: memo.localId, attachments: attachments)
                }

                if detail.hasPlace { place }

                if detail.onServer {
                    reactionsRow
                    referencesSection
                    commentsSection
                }

                Divider().padding(.top, 4)
                footer
            }
                        #if os(macOS)
            .padding(.horizontal, 40)
            .padding(.top, 32)
            #else
            .padding(.horizontal, 20)
            .padding(.top, 8)
            #endif
            .padding(.bottom, 48)
            .frame(maxWidth: Theme.readingWidth + 80, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .background(Theme.canvas)
        .task(id: detail.row.localId) {
            attachments = await model.attachments(detail.row.localId)
            if detail.onServer { await model.loadSocial(detail.row.localId) }
        }
        .sheet(isPresented: $settingReminder) {
            ReminderSheet(model: model, memoLocalId: memo.localId)
        }
        .sheet(isPresented: $pickingReference) {
            ReferencePicker(model: model, memoLocalId: memo.localId)
        }
        .sheet(isPresented: $sharing) {
            ShareLinksSheet(model: model, memoLocalId: memo.localId)
        }
        .confirmationDialog("Delete this memo?", isPresented: $confirmingDelete) {
            Button("Delete", role: .destructive) { Task { await model.delete(memo.localId) } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You can undo for a few seconds afterwards.")
        }
        #if os(iOS)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
            guard let urls = try? result.get() else { return }
            Task { await attach(urls) }
        }
        #endif
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
                ToolbarMenu(symbol: "ellipsis", help: "More actions") {
                    Button("Edit", systemImage: "pencil", action: edit)
                        .disabled(memo.locked)
                    Button(memo.pinned ? "Unpin" : "Pin", systemImage: memo.pinned ? "pin.slash" : "pin") {
                        Task { await model.setPinned(memo.localId, !memo.pinned) }
                    }
                    Button(detail.archived ? "Unarchive" : "Archive", systemImage: detail.archived ? "tray.and.arrow.up" : "archivebox") {
                        Task { await model.setArchived(memo.localId, !detail.archived) }
                    }
                    ColourMenu(current: memo.colourHex) { name in
                        Task { await model.setColour(memo.localId, name) }
                    }
                    Divider()
                    Button("Add a reference…", systemImage: "link") { pickingReference = true }
                        .disabled(!detail.onServer)
                    Button("Share link…", systemImage: "square.and.arrow.up") { sharing = true }
                        .disabled(!detail.onServer)
                    #if os(iOS)
                    if detail.hasPlace {
                        Button("Remove location", systemImage: "mappin.slash") {
                            Task { await model.clearLocation(memo.localId) }
                        }
                    } else {
                        Button("Add location", systemImage: "location") {
                            Task { await addLocation() }
                        }
                    }
                    #endif
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
                    Button("Attach a file…", systemImage: "paperclip") {
                        Task { await attachFiles() }
                    }
                    Button("Remind me…", systemImage: "bell") { settingReminder = true }
                    Divider()
                    Button("Delete", systemImage: "trash", role: .destructive) { confirmingDelete = true }
                }
            }
            Text(memo.dateLabel)
                .font(Type.rowMeta)
                .foregroundStyle(Theme.inkSoft)
        }
    }

    // MARK: The social layer

    /// Who reacted how, and a way to add your own. Tapping one you already gave takes it back.
    private var reactionsRow: some View {
        HStack(spacing: 6) {
            ForEach(model.reactions, id: \.type) { reaction in
                Button {
                    Task { await model.toggleReaction(memo.localId, reaction.type) }
                } label: {
                    HStack(spacing: 4) {
                        Text(reaction.type)
                        Text("\(reaction.count)").font(Type.rowMeta).monospacedDigit()
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(reaction.mine ? Theme.accentSoft : Theme.card))
                    .overlay(Capsule().strokeBorder(reaction.mine ? Theme.accent : Theme.hairline))
                }
                .buttonStyle(.plain)
            }
            Menu {
                ForEach(model.quickReactions, id: \.self) { emoji in
                    Button(emoji) { Task { await model.toggleReaction(memo.localId, emoji) } }
                }
            } label: {
                Image(systemName: "face.smiling")
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.inkSoft)
                    .frame(width: 30, height: 26)
                    .background(Capsule().fill(Theme.card))
                    .overlay(Capsule().strokeBorder(Theme.hairline))
            }
            .menuIndicator(.hidden)
            #if os(macOS)
            .menuStyle(.borderlessButton)
            .frame(width: 30)
            #endif
            Spacer()
        }
    }

    @ViewBuilder
    private var referencesSection: some View {
        if !model.references.isEmpty || !model.backlinks.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                if !model.references.isEmpty {
                    sectionLabel("References")
                    ForEach(model.references, id: \.remoteName) { ref in
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.turn.down.right").font(.system(size: 11)).foregroundStyle(Theme.inkSoft)
                            Button {
                                Task {
                                    if let local = ref.localId { model.open(local) } else { await model.openRemote(ref.remoteName) }
                                }
                            } label: {
                                Text(ref.snippet.isEmpty ? ref.remoteName : ref.snippet)
                                    .font(Type.rowBody).foregroundStyle(Theme.accent).lineLimit(1)
                            }
                            .buttonStyle(.plain)
                            Spacer()
                            Button {
                                Task { await model.removeReference(memo.localId, ref.remoteName) }
                            } label: {
                                Image(systemName: "xmark").font(.system(size: 10)).foregroundStyle(Theme.inkSoft)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                if !model.backlinks.isEmpty {
                    sectionLabel("Referenced by").padding(.top, 4)
                    ForEach(model.backlinks, id: \.localId) { back in
                        Button { model.open(back.localId) } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "arrow.turn.up.left").font(.system(size: 11)).foregroundStyle(Theme.inkSoft)
                                Text(back.locked ? "Locked memo" : back.title)
                                    .font(Type.rowBody).foregroundStyle(Theme.accent).lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var commentsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel(model.comments.isEmpty ? "Comments" : "\(model.comments.count) comment\(model.comments.count == 1 ? "" : "s")")
            ForEach(model.comments, id: \.localId) { comment in
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(comment.mine ? "You" : comment.creator)
                            .font(Type.rowMeta.weight(.medium)).foregroundStyle(Theme.ink)
                        Text(comment.dateLabel).font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                        if comment.pending {
                            Text("· waiting to send").font(Type.rowMeta).foregroundStyle(Theme.warm)
                        }
                    }
                    MarkdownView(text: comment.body)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
                .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
            }
            HStack(spacing: 8) {
                TextField("Add a comment", text: $commentDraft, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .lineLimit(1...4)
                Button {
                    let text = commentDraft
                    commentDraft = ""
                    Task { await model.addComment(memo.localId, text) }
                } label: {
                    Image(systemName: "arrow.up.circle.fill").font(.system(size: 22))
                }
                .buttonStyle(.plain)
                .foregroundStyle(commentDraft.trimmingCharacters(in: .whitespaces).isEmpty ? Theme.inkSoft.opacity(0.4) : Theme.accent)
                .disabled(commentDraft.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .frame(maxWidth: Theme.readingWidth)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(Type.label)
            .tracking(0.7)
            .foregroundStyle(Theme.inkSoft.opacity(0.8))
    }

    #if os(iOS)
    /// Asks the phone where it is, once, and names the place. Only on request, never in the
    /// background, and the fix goes to the memo on the user's own server and nowhere else.
    private func addLocation() async {
        do {
            let fix = try await LocationCapture.shared.current()
            await model.setLocation(memo.localId, latitude: fix.latitude, longitude: fix.longitude, placeName: fix.placeName)
        } catch {
            model.notice = "Could not get a location: \(error.localizedDescription)"
        }
    }
    #endif

    private var lockedNotice: some View {
        HStack(spacing: 12) {
            Image(systemName: "lock.fill").font(.system(size: 16)).foregroundStyle(Theme.inkSoft)
            VStack(alignment: .leading, spacing: 3) {
                Text("This memo is encrypted").font(Type.heading3)
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
                .linkButton()
                .font(Type.rowMeta)
        }
        .foregroundStyle(Theme.inkSoft)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Theme.accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
    }

        private var footer: some View {
        #if os(macOS)
        HStack(spacing: 26) {
            fact("Created", detail.created)
            fact("Last changed", detail.updated)
            fact("Visibility", detail.visibility)
            if !memo.locked { fact("Words", "\(detail.wordCount)") }
            Spacer()
        }
        #else
        // A phone is too narrow for four columns; one fact per line reads better than
        // labels broken mid-word.
        VStack(alignment: .leading, spacing: 8) {
            fact("Created", detail.created)
            fact("Last changed", detail.updated)
            fact("Visibility", detail.visibility)
            if !memo.locked { fact("Words", "\(detail.wordCount)") }
        }
        #endif
    }

    /// Where the memo was written. The coordinates are the memo's own, and no tile is fetched
    /// unless map previews are turned on.
    @ViewBuilder
    private var place: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "mappin.and.ellipse").font(.system(size: 11))
                Text(detail.placeName?.isEmpty == false ? detail.placeName! : "Somewhere unnamed")
                    .font(Type.rowMeta)
            }
            .foregroundStyle(Theme.inkSoft)

            if model.mapTiles {
                TileMapView(
                    points: [MapPoint(latitude: detail.latitude, longitude: detail.longitude)],
                    height: 170
                )
                .frame(maxWidth: Theme.readingWidth)
            } else {
                MapTilesOffNotice { Task { await model.setMapTiles(true) } }
                    .frame(maxWidth: Theme.readingWidth)
            }
        }
    }

    /// The standard picker: the app never reaches for a file the user has not chosen.
    private func attachFiles() async {
        #if os(macOS)
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.message = "Choose files to attach to this memo"
        guard panel.runModal() == .OK else { return }
        await attach(panel.urls)
        #else
        importing = true
        #endif
    }

    /// Files from the picker arrive security-scoped on iOS and must be opened before reading.
    private func attach(_ urls: [URL]) async {
        let scoped = urls.filter { $0.startAccessingSecurityScopedResource() }
        defer { scoped.forEach { $0.stopAccessingSecurityScopedResource() } }
        await model.attach(memo.localId, urls: urls)
        attachments = await model.attachments(memo.localId)
    }

    private func fact(_ name: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name.uppercased())
                .font(Type.label)
                .tracking(0.7)
                .foregroundStyle(Theme.inkSoft.opacity(0.8))
            Text(value).font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
        }
    }
}

/// Tags wrap rather than scroll, because a memo can carry a lot of them.
struct FlowTags: View {
    let tags: [String]
    var styles: [String: TagStyleRow] = [:]

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 60, maximum: 200), spacing: 6, alignment: .leading)],
                  alignment: .leading, spacing: 6) {
            ForEach(tags, id: \.self) { TagChip(tag: $0, style: styles[$0]) }
        }
    }
}

/// The sixteen tints, as a submenu. Nil clears the tint.
struct ColourMenu: View {
    let current: Int64
    let choose: (String?) -> Void

    var body: some View {
        Menu {
            Button("None", systemImage: current < 0 ? "checkmark" : "circle.slash") { choose(nil) }
            ForEach(NoteColour.entries, id: \.name) { colour in
                Button {
                    choose(colour.name)
                } label: {
                    Label(colour.name.capitalized, systemImage: current == colour.hex ? "checkmark.circle.fill" : "circle.fill")
                }
            }
        } label: {
            Label("Colour", systemImage: "paintpalette")
        }
    }
}

/// Picks a memo for this one to reference. Only memos the server already has can be named.
struct ReferencePicker: View {
    @ObservedObject var model: SessionModel
    let memoLocalId: String
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var candidates: [MemoRow] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Add a reference").font(Type.heading3)
                Spacer()
                Button("Cancel") { dismiss() }
            }
            .padding(14)
            Divider()
            TextField("Search memos", text: $query)
                .textFieldStyle(.roundedBorder)
                .padding(14)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 4) {
                    ForEach(candidates.filter { $0.localId != memoLocalId }, id: \.localId) { row in
                        Button {
                            Task {
                                await model.addReference(memoLocalId, to: row.localId)
                                dismiss()
                            }
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(row.locked ? "Locked memo" : row.title).font(Type.rowTitle).foregroundStyle(Theme.ink).lineLimit(1)
                                Text(row.dateLabel).font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 14)
            }
        }
        .sheetWidth(440)
        .frame(minHeight: 360)
        .background(Theme.canvas)
        .task(id: query) { candidates = await model.referenceCandidates(query) }
    }
}

/// Public links to a memo: make one, copy it, take it back.
struct ShareLinksSheet: View {
    @ObservedObject var model: SessionModel
    let memoLocalId: String
    @Environment(\.dismiss) private var dismiss
    @State private var shares: [ShareRow] = []
    @State private var expiresInDays = 0
    @State private var working = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Share links").font(Type.heading3)
                Spacer()
                Button("Done") { dismiss() }
            }
            .padding(14)
            Divider()
            VStack(alignment: .leading, spacing: 12) {
                if shares.isEmpty {
                    Text("No public links yet. Anyone with a link can read this memo without signing in.")
                        .font(Type.rowBody).foregroundStyle(Theme.inkSoft)
                        .fixedSize(horizontal: false, vertical: true)
                }
                ForEach(shares, id: \.name) { share in
                    HStack(spacing: 8) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(share.url).font(Type.rowMeta).foregroundStyle(Theme.ink).lineLimit(1).truncationMode(.middle)
                            Text(share.expiresLabel.map { "Expires \($0)" } ?? "Does not expire")
                                .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                        }
                        Spacer()
                        if let url = URL(string: share.url) {
                            ShareLink(item: url) { Image(systemName: "square.and.arrow.up") }
                                .buttonStyle(.plain).foregroundStyle(Theme.accent)
                        }
                        Button("Revoke") {
                            Task {
                                await model.revokeShare(memoLocalId, share.name)
                                shares = await model.shares(memoLocalId)
                            }
                        }
                        .linkButton().font(Type.rowMeta).foregroundStyle(Theme.danger)
                    }
                    .padding(10)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
                }
                Divider()
                HStack {
                    Picker("Expires", selection: $expiresInDays) {
                        Text("Never").tag(0)
                        Text("In a day").tag(1)
                        Text("In a week").tag(7)
                        Text("In a month").tag(30)
                    }
                    Spacer()
                    Button("Create link") {
                        working = true
                        Task {
                            _ = await model.createShare(memoLocalId, expiresInDays: expiresInDays)
                            shares = await model.shares(memoLocalId)
                            working = false
                        }
                    }
                    .disabled(working)
                }
            }
            .padding(14)
        }
        .sheetWidth(440)
        .background(Theme.canvas)
        .task { shares = await model.shares(memoLocalId) }
    }
}
