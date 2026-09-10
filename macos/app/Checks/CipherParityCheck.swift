import Shared

/// Proves a memo locked on Android opens here, and that what this platform seals can be
/// opened back. The vectors are the ones AndroidCryptoParityTest checks on a device and the
/// crypto-parity spike checks against the JVM, so all three agree or this fails.
///
/// Run with `macos/app/check-cipher.sh`.
@main
struct CipherCheck {
    struct Vector { let label: String; let password: String; let plaintext: String; let stored: String }

    static let vectors = [
        Vector(label: "ascii-memo", password: "correct horse battery staple",
               plaintext: "Buy milk\n- [ ] and bread",
               stored: "mymemos-enc:v1:AQIDBAUGBwgJCgsMDQ4PEKChoqOkpaanqKmqq29bkoi8l+FkS2XERnV4kkrTYx4k4fD+nNxMT9ESweUapFOGKQcFKI8="),
        Vector(label: "unicode-password", password: "pa\u{00DF}wort-\u{00E9}\u{00E8}-\u{4F60}\u{597D}",
               plaintext: "Locked note",
               stored: "mymemos-enc:v1:AQIDBAUGBwgJCgsMDQ4PEKChoqOkpaanqKmqq/0VtW7MkAnwZgWbQqingx07qE4jF/MCe9E6PQ=="),
        Vector(label: "emoji-password", password: "keys\u{1F511}\u{1F510}",
               plaintext: "Tagged \u{00A3} and \u{20AC}",
               stored: "mymemos-enc:v1:AQIDBAUGBwgJCgsMDQ4PEKChoqOkpaanqKmqqw01rymXqnZXjy4R22N3RgU/cblMDx17Sn6VvUoSidnb1Q=="),
        Vector(label: "empty-plaintext", password: "short", plaintext: "",
               stored: "mymemos-enc:v1:AQIDBAUGBwgJCgsMDQ4PEKChoqOkpaanqKmqq7hQMwmy37mULbhpcQgoS4g="),
    ]

    static func chars(_ s: String) -> KotlinCharArray {
        let scalars = Array(s.utf16)
        let array = KotlinCharArray(size: Int32(scalars.count))
        for (i, u) in scalars.enumerated() { array.set(index: Int32(i), value: u) }
        return array
    }

    static func main() {
        MacCrypto.shared.provider = AppleCrypto()
        var failures = 0

        for v in vectors {
            let opened = MemoCipher.shared.decrypt(content: v.stored, password: chars(v.password))
            if opened != v.plaintext {
                failures += 1
                print("  FAIL \(v.label): opened to \(opened.debugDescription)")
            } else {
                print("  ok   \(v.label): opened a memo locked on Android")
            }

            // And the other direction: seal here, open here.
            let resealed = MemoCipher.shared.encrypt(plain: v.plaintext, password: chars(v.password))
            let round = MemoCipher.shared.decrypt(content: resealed, password: chars(v.password))
            if round != v.plaintext { failures += 1; print("  FAIL \(v.label): round trip") }
        }

        print(failures == 0 ? "all vectors opened" : "\(failures) failures")
    }
}
