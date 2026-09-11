import SwiftUI
import Shared
#if os(iOS)
import PhotosUI
#endif

/// Writing a memo.
///
/// Markdown in, Markdown out: no rich-text editing, because the memo is stored as Markdown and
/// a WYSIWYG layer over that is a good way to lose someone's formatting. There is a live
/// preview instead, which shows what the text will look like without pretending to be it.
struct EditorView: View {
    @ObservedObject var model: SessionModel
    @Environment(\.dismiss) private var dismiss

    let editing: String?
    var initialText: String? = nil

    @State private var text = ""
    @State private var visibility = "PRIVATE"
    @State private var pinned = false
    @State private var showPreview = false
    @State private var saving = false
    @State private var loaded = false
    @State private var pendingEdit: EditorEdit?
    @State private var currentWord: String?
    @State private var tagSuggestions: [String] = []
    #if os(iOS)
    @State private var pickedPhotos: [PhotosPickerItem] = []
    /// Photos picked for a memo that does not exist yet, attached once it does.
    @State private var pendingPhotos: [URL] = []
    #endif

    private var isNew: Bool { editing == nil }

    var body: some View {
        #if os(macOS)
        panel
        #else
        NavigationStack {
            panel
                .navigationTitle(isNew ? "New memo" : "Edit memo")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(isNew ? "Save" : "Update") { Task { await save() } }
                            .disabled(saving || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
        }
        .interactiveDismissDisabled(!text.isEmpty)
        #endif
    }

    private var panel: some View {
        VStack(spacing: 0) {
            #if os(macOS)
            toolbar
            #else
            options
            #endif
            Divider()

            FormatBar { pendingEdit = $0 }
            Divider()

            if let word = currentWord {
                SuggestionBar(word: word, tags: tagSuggestions, dates: model.dateSuggestions(String(word.dropFirst()))) {
                    pendingEdit = .replaceWord($0)
                }
                Divider()
            }

            #if os(macOS)
            HStack(spacing: 0) {
                editor
                if showPreview {
                    Divider()
                    preview
                }
            }
            #else
            // A phone has no room for two columns: the preview takes the editor's place.
            if showPreview { preview } else { editor }
            #endif
        }
        #if os(macOS)
        .frame(minWidth: showPreview ? 860 : 560, minHeight: 460)
        #endif
        .background(Theme.canvas)
        .task { await load() }
        .task(id: currentWord) {
            guard let word = currentWord, word.hasPrefix("#") else { tagSuggestions = []; return }
            tagSuggestions = await model.tagSuggestions(String(word.dropFirst()))
        }
        #if os(iOS)
        .onChange(of: pickedPhotos) { _, items in Task { await stagePhotos(items) } }
        #endif
    }

    // MARK: Pieces

    #if os(iOS)
    /// Visibility, pin and preview on a phone: the buttons that are not Cancel or Save.
    private var options: some View {
        HStack(spacing: 14) {
            Picker("Visibility", selection: $visibility) {
                Text("Private").tag("PRIVATE")
                Text("Protected").tag("PROTECTED")
                Text("Public").tag("PUBLIC")
            }
            .labelsHidden()
            .fixedSize()

            Spacer()

            Toggle(isOn: $pinned) {
                Image(systemName: pinned ? "pin.fill" : "pin")
            }
            .toggleStyle(.button)

            Toggle(isOn: $showPreview) {
                Image(systemName: showPreview ? "eye.fill" : "eye")
            }
            .toggleStyle(.button)

            PhotosPicker(selection: $pickedPhotos, matching: .images) {
                Image(systemName: pendingPhotos.isEmpty ? "photo" : "photo.badge.checkmark")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
    }

    /// Copies picked photos to temporary files so they can be attached like any other file.
    private func stagePhotos(_ items: [PhotosPickerItem]) async {
        for item in items {
            guard let data = try? await item.loadTransferable(type: Data.self) else { continue }
            let ext = item.supportedContentTypes.first?.preferredFilenameExtension ?? "jpg"
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("photo-\(UUID().uuidString).\(ext)")
            guard (try? data.write(to: url)) != nil else { continue }
            if let editing {
                await model.attach(editing, urls: [url])
            } else {
                pendingPhotos.append(url)
            }
        }
        pickedPhotos = []
    }
    #endif

    private var toolbar: some View {
        HStack(spacing: 12) {
            Text(isNew ? "New memo" : "Edit memo")
                .font(Type.rowTitle)

            Spacer()

            Picker("", selection: $visibility) {
                Text("Private").tag("PRIVATE")
                Text("Protected").tag("PROTECTED")
                Text("Public").tag("PUBLIC")
            }
            .labelsHidden()
            .frame(width: 120)
            .help("Who can see this memo on the server")

            Toggle(isOn: $pinned) {
                Image(systemName: pinned ? "pin.fill" : "pin")
            }
            .toggleStyle(.button)
            .help("Pin to the top of the timeline")

            Toggle(isOn: $showPreview) {
                Image(systemName: "sidebar.right")
            }
            .toggleStyle(.button)
            .help("Show a preview")

            Divider().frame(height: 16)

            Button("Cancel") { dismiss() }
                .keyboardShortcut(.cancelAction)

            Button(isNew ? "Save" : "Update") { Task { await save() } }
                .keyboardShortcut(.defaultAction)
                .disabled(saving || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    private var editor: some View {
        ZStack(alignment: .topLeading) {
            MarkdownEditor(text: $text, pendingEdit: $pendingEdit, currentWord: $currentWord, session: model.session)

            if text.isEmpty {
                Text("Write something. The buttons above add Markdown, or type it yourself.")
                    .font(Type.body)
                    .foregroundStyle(Theme.inkSoft.opacity(0.6))
                    .padding(.horizontal, 19)
                    .padding(.vertical, 21)
                    .allowsHitTesting(false)
            }
        }
        #if os(macOS)
        .frame(minWidth: 420)
        #endif
        .background(Theme.card)
    }

    private var preview: some View {
        ScrollView {
            MarkdownView(text: text)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
        }
        #if os(macOS)
        .frame(minWidth: 320)
        #endif
        .background(Theme.canvas)
    }

    // MARK: Actions

    private func load() async {
        guard !loaded else { return }
        loaded = true
        if let editing {
            text = await model.rawContent(editing) ?? ""
            pinned = model.memo(editing)?.pinned ?? false
        } else if let initialText {
            text = initialText
        }
    }

    private func save() async {
        saving = true
        defer { saving = false }
        let localId = await model.save(editing: editing, text: text, visibility: visibility, pinned: pinned)
        #if os(iOS)
        if let localId, !pendingPhotos.isEmpty {
            await model.attach(localId, urls: pendingPhotos)
            pendingPhotos = []
        }
        #endif
        _ = localId
        dismiss()
    }
}
