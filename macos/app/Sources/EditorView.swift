import SwiftUI
import Shared

/// Writing a memo.
///
/// Markdown in, Markdown out: no rich-text editing, because the memo is stored as Markdown and
/// a WYSIWYG layer over that is a good way to lose someone's formatting. There is a live
/// preview instead, which shows what the text will look like without pretending to be it.
struct EditorView: View {
    @ObservedObject var model: SessionModel
    @Environment(\.dismiss) private var dismiss

    let editing: String?

    @State private var text = ""
    @State private var visibility = "PRIVATE"
    @State private var pinned = false
    @State private var showPreview = false
    @State private var saving = false
    @State private var loaded = false
    @FocusState private var editorFocused: Bool

    private var isNew: Bool { editing == nil }

    var body: some View {
        VStack(spacing: 0) {
            toolbar
            Divider()

            HStack(spacing: 0) {
                editor
                if showPreview {
                    Divider()
                    preview
                }
            }
        }
        .frame(minWidth: showPreview ? 860 : 560, minHeight: 460)
        .background(Theme.canvas)
        .task { await load() }
    }

    // MARK: Pieces

    private var toolbar: some View {
        HStack(spacing: 12) {
            Text(isNew ? "New memo" : "Edit memo")
                .font(.system(size: 13, weight: .semibold))

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
            TextEditor(text: $text)
                .font(Type.body)
                .lineSpacing(Theme.readingLeading)
                .scrollContentBackground(.hidden)
                .background(Theme.card)
                .focused($editorFocused)
                .padding(.horizontal, 18)
                .padding(.vertical, 16)

            if text.isEmpty {
                Text("Write something. Markdown works: # headings, - lists, - [ ] tasks, #tags.")
                    .font(Type.body)
                    .foregroundStyle(Theme.inkSoft.opacity(0.7))
                    .padding(.horizontal, 23)
                    .padding(.vertical, 24)
                    .allowsHitTesting(false)
            }
        }
        .frame(minWidth: 420)
        .background(Theme.card)
    }

    private var preview: some View {
        ScrollView {
            MarkdownView(text: text)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
        }
        .frame(minWidth: 320)
        .background(Theme.canvas)
    }

    // MARK: Actions

    private func load() async {
        guard !loaded else { return }
        loaded = true
        if let editing {
            text = await model.rawContent(editing) ?? ""
            pinned = model.memo(editing)?.pinned ?? false
        }
        editorFocused = true
    }

    private func save() async {
        saving = true
        defer { saving = false }
        await model.save(editing: editing, text: text, visibility: visibility, pinned: pinned)
        dismiss()
    }
}
