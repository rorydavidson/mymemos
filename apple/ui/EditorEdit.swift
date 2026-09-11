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

    var id: String {
        switch self {
        case let .wrap(marker): return "wrap-\(marker)"
        case let .prefixLines(marker): return "prefix-\(marker)"
        case let .insert(text): return "insert-\(text)"
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
        }
    }
}
