import Shared

/// Reproduces what happens when Encrypt is chosen with no password held yet.
@main
struct EncryptFlowCheck {
    static func main() async throws {
        MacCrypto.shared.provider = AppleCrypto()
        let session = MemosSession()
        guard (try await session.signedInAs()) != nil else { print("not signed in"); return }

        let text = "# Encrypt flow check\n\nsafe to delete"
        guard let id = try await session.create(content: text, visibility: "PRIVATE", pinned: false) else { return }

        session.forgetPassword()
        print("no password held: \(!session.hasPassword)")

        // Step one: the user picks Encrypt. This is what used to return false and stop.
        let firstTry = try await session.lock(localId: id)
        print("encrypt without a password refuses: \(firstTry == false)")

        // Step two: the sheet supplies one, and the app must now finish the job.
        session.usePassword(password: "a good password", remember: false)
        let retry = try await session.lock(localId: id)
        print("encrypt after the password arrives: \(retry == true)")

        let after = try await session.memo(localId: id)
        print("memo is now locked: \(after?.row.locked ?? false)")
        print("stored body is empty: \((after?.row.body ?? "x").isEmpty)")

        let shown = try await session.reveal(localId: id)
        print("reveals back to the original: \(shown.text == text)")

        _ = try await session.unlockForGood(localId: id)
        let cleared = try await session.memo(localId: id)
        print("encryption removed again: \(cleared?.row.locked == false)")

        _ = try await session.delete(localId: id)
        session.forgetPassword()
        let left = try await session.timeline()
        print("\ncleaned up: \(!left.flatMap(\.memos).contains { $0.localId == id })")
    }
}
