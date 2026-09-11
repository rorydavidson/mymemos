import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// The share sheet's view of MyMemos: what was shared, a place to add a line, and Save.
/// Nothing here talks to the server or the database. It leaves the payload in the app
/// group's inbox, and the app picks it up the next time it comes to the front and opens
/// the editor with it, the way the Android share target opens the editor prefilled.
@objc(ShareViewController)
final class ShareViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        let host = UIHostingController(rootView: ShareView(
            items: extensionContext?.inputItems as? [NSExtensionItem] ?? [],
            finish: { [weak self] in self?.extensionContext?.completeRequest(returningItems: nil) },
            cancel: { [weak self] in
                self?.extensionContext?.cancelRequest(withError: NSError(domain: "com.keltruc.mymemos", code: 0))
            }
        ))
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
    }
}

private struct ShareView: View {
    let items: [NSExtensionItem]
    let finish: () -> Void
    let cancel: () -> Void

    @State private var text = ""
    @State private var images: [String] = []
    @State private var loaded = false
    @State private var saving = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                TextEditor(text: $text)
                    .font(.body)
                    .frame(minHeight: 140)
                    .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(.secondary.opacity(0.3)))
                if !images.isEmpty {
                    Label("\(images.count) image\(images.count == 1 ? "" : "s") attached", systemImage: "photo")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Text("Saved as a new memo the next time MyMemos opens.")
                    .font(.footnote).foregroundStyle(.secondary)
                Spacer()
            }
            .padding()
            .navigationTitle("New memo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: cancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }.disabled(saving || (text.isEmpty && images.isEmpty))
                }
            }
        }
        .task { await load() }
    }

    private func save() {
        saving = true
        try? AppGroup.leave(AppGroup.Shared(text: text, images: images, receivedAt: Date()))
        finish()
    }

    /// Text, a URL, and images, in whatever mix the sending app offered.
    private func load() async {
        guard !loaded else { return }
        loaded = true
        var lines: [String] = []
        for item in items {
            for provider in item.attachments ?? [] {
                if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
                    if let url = try? await provider.loadItem(forTypeIdentifier: UTType.image.identifier) as? URL,
                       let destination = AppGroup.imageDestination(extension: url.pathExtension.isEmpty ? "jpg" : url.pathExtension),
                       (try? FileManager.default.copyItem(at: url, to: destination)) != nil {
                        images.append(destination.lastPathComponent)
                    } else if let image = try? await provider.loadItem(forTypeIdentifier: UTType.image.identifier) as? UIImage,
                              let data = image.jpegData(compressionQuality: 0.9),
                              let destination = AppGroup.imageDestination(extension: "jpg"),
                              (try? data.write(to: destination)) != nil {
                        images.append(destination.lastPathComponent)
                    }
                } else if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                    if let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
                        lines.append(url.absoluteString)
                    }
                } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                    if let string = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) as? String {
                        lines.append(string)
                    }
                }
            }
            if lines.isEmpty, let title = item.attributedContentText?.string, !title.isEmpty { lines.append(title) }
        }
        text = lines.joined(separator: "\n")
    }
}
