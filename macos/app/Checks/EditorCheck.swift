import Shared

/// The shared list continuation, as the editor calls it on Return.
@main
struct EditorCheck {
    static func main() async throws {
        MacCrypto.shared.provider = AppleCrypto()
        let session = MemosSession()
        var failures = 0

        /// Presses Return at the end of `before`, the way the editor does: the newline lands
        /// first, then the shared logic is asked what the field should say instead.
        func check(_ name: String, _ before: String, expect: String?) {
            let after = before + "\n"
            let result = session.continueAfterReturn(
                beforeText: before, beforeCursor: Int32(before.count),
                afterText: after, afterCursor: Int32(after.count)
            )
            let got = result?.text
            let ok = got == expect
            print(ok ? "ok   \(name)" : "FAIL \(name): got \(got?.debugDescription ?? "nil")")
            if !ok { failures += 1 }
        }

        check("a bullet carries down", "- milk", expect: "- milk\n- ")
        check("a task starts the next one unticked", "- [ ] milk", expect: "- [ ] milk\n- [ ] ")
        check("a ticked task does not carry the tick", "- [x] milk", expect: "- [x] milk\n- [ ] ")
        check("a numbered item increments", "1. first", expect: "1. first\n2. ")
        check("indentation is kept", "  - nested", expect: "  - nested\n  - ")
        check("an empty item clears the marker instead", "- milk\n- ", expect: "- milk\n")
        check("plain text is left to the normal Return", "just text", expect: nil)

        print(failures == 0 ? "\neditor continuation passed" : "\n\(failures) failure(s)")
    }
}
