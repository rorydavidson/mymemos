import SwiftUI
import UniformTypeIdentifiers
import Shared

/// The files under a memo. Images show themselves; anything else is a card you can open.
struct AttachmentStrip: View {
    @ObservedObject var model: SessionModel
    let memoLocalId: String
    let attachments: [AttachmentRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(attachments.count) attachment\(attachments.count == 1 ? "" : "s")".uppercased())
                .font(Type.label)
                .tracking(0.7)
                .foregroundStyle(Theme.inkSoft.opacity(0.8))

            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 150, maximum: 220), spacing: 10, alignment: .leading)],
                alignment: .leading,
                spacing: 10
            ) {
                ForEach(attachments, id: \.localId) { attachment in
                    AttachmentTile(model: model, memoLocalId: memoLocalId, attachment: attachment)
                }
            }
        }
    }
}

private struct AttachmentTile: View {
    @ObservedObject var model: SessionModel
    let memoLocalId: String
    let attachment: AttachmentRow

    @State private var image: NSImage?
    @State private var loading = true

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            preview
            details
        }
        .background(Theme.card)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
        .contentShape(Rectangle())
        .onTapGesture { Task { await open() } }
        .help("Open \(attachment.filename)")
        .task { await load() }
    }

    @ViewBuilder
    private var preview: some View {
        ZStack {
            Theme.canvas
            if let image {
                Image(nsImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else if loading, attachment.isImage {
                ProgressView().controlSize(.small)
            } else {
                Image(systemName: symbol)
                    .font(.system(size: 24, weight: .light))
                    .foregroundStyle(Theme.inkSoft)
            }
        }
        .frame(height: 104)
        .clipped()
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(attachment.filename)
                .font(Type.rowMeta.weight(.medium))
                .lineLimit(1)
                .truncationMode(.middle)
            HStack(spacing: 5) {
                Text(size)
                if !attachment.uploaded {
                    Text("· waiting to upload").foregroundStyle(Theme.warm)
                }
            }
            .font(Type.rowMeta)
            .foregroundStyle(Theme.inkSoft)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 9)
        .padding(.vertical, 7)
    }

    private var symbol: String {
        if attachment.mimeType.hasPrefix("video/") { return "film" }
        if attachment.mimeType.hasPrefix("audio/") { return "waveform" }
        if attachment.mimeType.contains("pdf") { return "doc.richtext" }
        if attachment.mimeType.hasPrefix("text/") { return "doc.text" }
        return "paperclip"
    }

    private var size: String {
        ByteCountFormatter.string(fromByteCount: attachment.sizeBytes, countStyle: .file)
    }

    private func load() async {
        defer { loading = false }
        guard attachment.isImage else { return }
        guard let data = await model.attachmentData(memoLocalId, attachment.localId) else { return }
        image = NSImage(data: data)
    }

    /// Writes the bytes somewhere the system can open, then hands it over.
    private func open() async {
        guard let data = await model.attachmentData(memoLocalId, attachment.localId) else { return }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("MyMemos", isDirectory: true)
            .appendingPathComponent(attachment.filename)
        try? FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(), withIntermediateDirectories: true
        )
        guard (try? data.write(to: url)) != nil else { return }
        NSWorkspace.shared.open(url)
    }
}
