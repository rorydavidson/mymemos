import AppKit
import SwiftUI
import Shared

/// The text view behind the editor.
///
/// SwiftUI's TextEditor gives no access to the selection and no hook on Return, and both are
/// needed here: a formatting button has to know what is selected to wrap it, and pressing
/// Return inside a list has to carry the marker down, which is shared logic the phone already
/// uses. So this wraps NSTextView.
struct MarkdownEditor: NSViewRepresentable {
    @Binding var text: String
    /// Set to ask the view to apply an edit; cleared once it has.
    @Binding var pendingEdit: EditorEdit?
    /// The `#tag` or `@date` under the cursor, for the completion bar. Nil when there is none.
    @Binding var currentWord: String?
    let session: MemosSession
    /// Puts the cursor in the text, at the end, as soon as the view is on screen.
    var focusOnAppear = false
    /// Called when the text view gives up focus to something else in the window.
    var onEndEditing: (() -> Void)? = nil

    func makeNSView(context: Context) -> NSScrollView {
        let scroll = NSTextView.scrollableTextView()
        guard let view = scroll.documentView as? NSTextView else { return scroll }

        view.delegate = context.coordinator
        view.string = text
        view.isRichText = false
        view.allowsUndo = true
        view.font = NSFont(name: "Google Sans Flex", size: 15) ?? .systemFont(ofSize: 15)
        view.textContainerInset = NSSize(width: 14, height: 16)
        view.drawsBackground = false
        // Smart quotes turn a Markdown quote into a curly one, and smart dashes turn "--" into
        // an em dash. Both corrupt text that is meant to be read literally.
        view.isAutomaticQuoteSubstitutionEnabled = false
        view.isAutomaticDashSubstitutionEnabled = false
        view.isAutomaticTextReplacementEnabled = false

        scroll.drawsBackground = false
        context.coordinator.textView = view
        context.coordinator.prime(view)
        if focusOnAppear {
            // The window is not there yet while the view is being made.
            DispatchQueue.main.async {
                view.window?.makeFirstResponder(view)
                view.setSelectedRange(NSRange(location: (view.string as NSString).length, length: 0))
                view.scrollRangeToVisible(view.selectedRange())
            }
        }
        return scroll
    }

    func updateNSView(_ scroll: NSScrollView, context: Context) {
        guard let view = scroll.documentView as? NSTextView else { return }
        if view.string != text { view.string = text }

        // Applied once. Clearing during an update loops, so the request is handed back on the
        // next turn, and any redraw before then must not apply it again: each one would wrap
        // the text in another pair of markers.
        if let edit = pendingEdit, !context.coordinator.applyingEdit {
            context.coordinator.applyingEdit = true
            context.coordinator.apply(edit, to: view)
            DispatchQueue.main.async {
                pendingEdit = nil
                context.coordinator.applyingEdit = false
            }
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, NSTextViewDelegate {
        var applyingEdit = false
        private let parent: MarkdownEditor
        weak var textView: NSTextView?

        init(_ parent: MarkdownEditor) {
            self.parent = parent
        }

        func prime(_ view: NSTextView) {
            snapshot(view)
        }

        /// The field as it was before the current keystroke, which is what the shared list
        /// continuation compares against. It runs after the newline has landed rather than
        /// instead of it, so intercepting Return before the insert would never match.
        private var previousText = ""
        private var previousCursor = 0

        func textDidChange(_ notification: Foundation.Notification) {
            guard let view = notification.object as? NSTextView else { return }

            if let result = parent.session.continueAfterReturn(
                beforeText: previousText,
                beforeCursor: Int32(previousCursor),
                afterText: view.string,
                afterCursor: Int32(view.selectedRange().location)
            ) {
                replace(view, with: result.text, cursor: Int(result.cursor))
            } else {
                parent.text = view.string
            }
            snapshot(view)
        }

        func textDidEndEditing(_ notification: Foundation.Notification) {
            parent.onEndEditing?()
        }

        func textViewDidChangeSelection(_ notification: Foundation.Notification) {
            guard let view = notification.object as? NSTextView else { return }
            snapshot(view)
        }

        private func reportWord(_ view: NSTextView) {
            let word = EditorEdit.wordAtCursor(in: view.string, cursor: view.selectedRange().location)?.0
            if parent.currentWord != word { DispatchQueue.main.async { self.parent.currentWord = word } }
        }

        private func snapshot(_ view: NSTextView) {
            previousText = view.string
            previousCursor = view.selectedRange().location
            reportWord(view)
        }

        func apply(_ edit: EditorEdit, to view: NSTextView) {
            var range = view.selectedRange()
            if case .replaceWord = edit, let (_, wordRange) = EditorEdit.wordAtCursor(in: view.string, cursor: range.location) {
                range = wordRange
            }
            let text = view.string as NSString
            let selected = text.substring(with: range)
            let (replacement, cursorOffset) = edit.apply(to: selected, wholeText: view.string, at: range)
            let updated = text.replacingCharacters(in: range, with: replacement)
            replace(view, with: updated, cursor: range.location + cursorOffset)
        }

        /// One undoable edit, rather than clearing and retyping the document.
        private func replace(_ view: NSTextView, with updated: String, cursor: Int) {
            let whole = NSRange(location: 0, length: (view.string as NSString).length)
            if view.shouldChangeText(in: whole, replacementString: updated) {
                view.textStorage?.replaceCharacters(in: whole, with: updated)
                view.didChangeText()
            }
            let bounded = max(0, min(cursor, (view.string as NSString).length))
            view.setSelectedRange(NSRange(location: bounded, length: 0))
            view.scrollRangeToVisible(view.selectedRange())
            parent.text = view.string
        }
    }
}
