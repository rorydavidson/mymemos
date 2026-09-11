import SwiftUI
import UIKit
import Shared

/// The text view behind the editor on iOS.
///
/// SwiftUI's TextEditor gives no access to the selection and no hook on Return, and both are
/// needed: a formatting button has to know what is selected to wrap it, and Return inside a
/// list has to carry the marker down, which is shared logic the phone and the Mac already use.
/// So this wraps UITextView, the same shape as the Mac's NSTextView wrapper.
struct MarkdownEditor: UIViewRepresentable {
    @Binding var text: String
    /// Set to ask the view to apply an edit; cleared once it has.
    @Binding var pendingEdit: EditorEdit?
    /// The `#tag` or `@date` under the cursor, for the completion bar. Nil when there is none.
    @Binding var currentWord: String?
    let session: MemosSession

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.delegate = context.coordinator
        view.text = text
        view.font = UIFont(name: "Google Sans Flex", size: 17) ?? .systemFont(ofSize: 17)
        view.textColor = UIColor(Theme.ink)
        view.textContainerInset = UIEdgeInsets(top: 16, left: 10, bottom: 16, right: 10)
        view.backgroundColor = .clear
        view.alwaysBounceVertical = true
        view.keyboardDismissMode = .interactive
        // Smart quotes turn a Markdown quote into a curly one, and smart dashes turn "--" into
        // an em dash. Both corrupt text that is meant to be read literally. Autocorrect stays,
        // as it does on Android: the continuation logic already copes with it recasing a word.
        view.smartQuotesType = .no
        view.smartDashesType = .no
        view.smartInsertDeleteType = .no
        context.coordinator.prime(view)
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        if view.text != text { view.text = text }

        if let edit = pendingEdit {
            context.coordinator.apply(edit, to: view)
            // Clearing during an update loops, so hand it back on the next turn.
            DispatchQueue.main.async { pendingEdit = nil }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UITextViewDelegate {
        private let parent: MarkdownEditor

        init(_ parent: MarkdownEditor) {
            self.parent = parent
        }

        func prime(_ view: UITextView) {
            snapshot(view)
        }

        /// The field as it was before the current keystroke, which is what the shared list
        /// continuation compares against. It runs after the newline has landed rather than
        /// instead of it, so intercepting Return before the insert would never match.
        private var previousText = ""
        private var previousCursor = 0

        func textViewDidChange(_ view: UITextView) {
            if let result = parent.session.continueAfterReturn(
                beforeText: previousText,
                beforeCursor: Int32(previousCursor),
                afterText: view.text,
                afterCursor: Int32(view.selectedRange.location)
            ) {
                replace(view, with: result.text, cursor: Int(result.cursor))
            } else {
                parent.text = view.text
            }
            snapshot(view)
        }

        func textViewDidChangeSelection(_ view: UITextView) {
            snapshot(view)
        }

        private func snapshot(_ view: UITextView) {
            previousText = view.text
            previousCursor = view.selectedRange.location
            let word = EditorEdit.wordAtCursor(in: view.text, cursor: view.selectedRange.location)?.0
            if parent.currentWord != word { DispatchQueue.main.async { self.parent.currentWord = word } }
        }

        func apply(_ edit: EditorEdit, to view: UITextView) {
            var range = view.selectedRange
            if case .replaceWord = edit, let (_, wordRange) = EditorEdit.wordAtCursor(in: view.text, cursor: range.location) {
                range = wordRange
            }
            let text = view.text as NSString
            let selected = text.substring(with: range)
            let (replacement, cursorOffset) = edit.apply(to: selected, wholeText: view.text, at: range)
            let updated = text.replacingCharacters(in: range, with: replacement)
            replace(view, with: updated, cursor: range.location + cursorOffset)
        }

        /// Replaces the whole text without going through the delegate again, keeping the
        /// typing attributes so an empty document does not lose its font.
        private func replace(_ view: UITextView, with updated: String, cursor: Int) {
            let whole = NSRange(location: 0, length: (view.text as NSString).length)
            view.textStorage.replaceCharacters(
                in: whole,
                with: NSAttributedString(string: updated, attributes: view.typingAttributes)
            )
            let bounded = max(0, min(cursor, (view.text as NSString).length))
            view.selectedRange = NSRange(location: bounded, length: 0)
            view.scrollRangeToVisible(view.selectedRange)
            parent.text = view.text
        }
    }
}
