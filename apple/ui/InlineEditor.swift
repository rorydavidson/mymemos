#if os(macOS)
import SwiftUI
import Shared

/// Editing a memo where it is read, in the detail pane, so a small change does not mean
/// opening a window. The sheet editor is still there for the preview and more room.
///
/// It edits the raw Markdown, as the sheet does: what is typed is what is saved.
struct InlineEditor: View {
    @ObservedObject var model: SessionModel
    @Binding var text: String
    let done: () -> Void
    let cancel: () -> Void
    let popOut: () -> Void

    @State private var pendingEdit: EditorEdit?
    @State private var currentWord: String?
    @State private var tagSuggestions: [String] = []

    var body: some View {
        VStack(spacing: 0) {
            FormatBar { pendingEdit = $0 }

            if let word = currentWord {
                Divider()
                SuggestionBar(word: word, tags: tagSuggestions, dates: model.dateSuggestions(String(word.dropFirst()))) {
                    pendingEdit = .replaceWord($0)
                }
            }

            Divider()
            MarkdownEditor(
                text: $text,
                pendingEdit: $pendingEdit,
                currentWord: $currentWord,
                session: model.session,
                focusOnAppear: true,
                onEndEditing: done
            )
            .frame(minHeight: 320)
            .background(Theme.card)

            Divider()
            HStack(spacing: 10) {
                Button("Open in editor", systemImage: "arrow.up.left.and.arrow.down.right", action: popOut)
                    .linkButton()
                    .font(Type.rowMeta)
                Spacer()
                Text("⌘↩ saves").font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                Button("Cancel", action: cancel)
                    .keyboardShortcut(.cancelAction)
                Button("Done", action: done)
                    .keyboardShortcut(.return, modifiers: .command)
            }
            .padding(10)
            .background(Theme.canvas)
        }
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Theme.hairline))
        .task(id: currentWord) {
            guard let word = currentWord, word.hasPrefix("#") else { tagSuggestions = []; return }
            tagSuggestions = await model.tagSuggestions(String(word.dropFirst()))
        }
    }
}
#endif
