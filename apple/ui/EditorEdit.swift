import Foundation

/// A formatting action the toolbar can ask the editor to perform. The editor itself is a
/// text view from the platform's own kit, so it is written twice; what an edit means is not.
enum EditorEdit: Identifiable {
    /// Puts markers either side of the selection, e.g. bold.
    case wrap(String)
    /// Puts a marker at the start of each selected line, e.g. a bullet.
    case prefixLines(String)
    /// Drops text in where the cursor is.
    case insert(String)
    /// Replaces the word the cursor is in (a `#tag` or `@date` being typed) with a completion.
    case replaceWord(String)

    var id: String {
        switch self {
        case let .wrap(marker): return "wrap-\(marker)"
        case let .prefixLines(marker): return "prefix-\(marker)"
        case let .insert(text): return "insert-\(text)"
        case let .replaceWord(text): return "word-\(text)"
        }
    }

    /// Returns the replacement text and where to leave the cursor within it.
    func apply(to selected: String, wholeText: String, at range: NSRange) -> (String, Int) {
        switch self {
        case let .wrap(marker):
            if selected.isEmpty { return (marker + marker, marker.count) }
            return (marker + selected + marker, (marker + selected + marker).count)

        case let .prefixLines(marker):
            // With nothing selected, the marker goes on the line the cursor is in.
            if selected.isEmpty {
                return (marker, marker.count)
            }
            let prefixed = selected
                .components(separatedBy: "\n")
                .map { $0.isEmpty ? $0 : marker + $0 }
                .joined(separator: "\n")
            return (prefixed, prefixed.count)

        case let .insert(text):
            return (text, text.count)

        case let .replaceWord(text):
            // The editor widens the range to the word first; here it is a plain replacement.
            return (text + " ", text.count + 1)
        }
    }

    /// The `#tag` or `@date` the cursor is in, and where it sits, or nil when the cursor is
    /// not inside one. Only these two sigils get completions.
    static func wordAtCursor(in text: String, cursor: Int) -> (String, NSRange)? {
        let ns = text as NSString
        guard cursor <= ns.length else { return nil }
        var start = cursor
        while start > 0 {
            let ch = ns.character(at: start - 1)
            if ch == 0x20 || ch == 0x0A || ch == 0x09 { break }
            start -= 1
        }
        guard start < cursor else { return nil }
        let word = ns.substring(with: NSRange(location: start, length: cursor - start))
        guard word.hasPrefix("#") || word.hasPrefix("@") else { return nil }
        // A second sigil mid-word ("a#b") is not a tag being typed.
        guard word.dropFirst().allSatisfy({ $0 != "#" && $0 != "@" }) else { return nil }
        return (word, NSRange(location: start, length: cursor - start))
    }
}
