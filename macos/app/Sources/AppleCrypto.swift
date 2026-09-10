import CommonCrypto
import CryptoKit
import Foundation
import Shared

/// AES-256-GCM and PBKDF2-HMAC-SHA256 for the shared data layer.
///
/// Kotlin/Native exposes PBKDF2 through CommonCrypto but no AES-GCM, so the cipher's
/// primitives are handed in from here. The crypto-parity spike proved this produces the same
/// bytes as the Android implementation, unicode and emoji passwords included; the same fixed
/// vectors are checked at startup in debug builds.
final class AppleCrypto: NSObject, CryptoProvider {

    func randomBytes(size: Int32) -> KotlinByteArray {
        var data = Data(count: Int(size))
        data.withUnsafeMutableBytes { _ = SecRandomCopyBytes(kSecRandomDefault, Int(size), $0.baseAddress!) }
        return data.toKotlin()
    }

    func deriveKey(password: String, salt: KotlinByteArray, iterations: Int32, keyBytes: Int32) -> KotlinByteArray {
        let saltData = salt.toData()
        var derived = Data(count: Int(keyBytes))
        let passwordBytes = Array(password.utf8)
        let status = derived.withUnsafeMutableBytes { derivedPtr in
            saltData.withUnsafeBytes { saltPtr in
                passwordBytes.withUnsafeBufferPointer { passPtr in
                    passPtr.baseAddress!.withMemoryRebound(to: CChar.self, capacity: passwordBytes.count) { passChars in
                        CCKeyDerivationPBKDF(
                            CCPBKDFAlgorithm(kCCPBKDF2),
                            passChars, passwordBytes.count,
                            saltPtr.bindMemory(to: UInt8.self).baseAddress, saltData.count,
                            CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), UInt32(iterations),
                            derivedPtr.bindMemory(to: UInt8.self).baseAddress, Int(keyBytes)
                        )
                    }
                }
            }
        }
        precondition(status == kCCSuccess, "PBKDF2 failed with \(status)")
        return derived.toKotlin()
    }

    func seal(key: KotlinByteArray, nonce: KotlinByteArray, plaintext: KotlinByteArray) -> KotlinByteArray {
        let symmetric = SymmetricKey(data: key.toData())
        let box = try! AES.GCM.seal(
            plaintext.toData(),
            using: symmetric,
            nonce: try! AES.GCM.Nonce(data: nonce.toData())
        )
        return (box.ciphertext + box.tag).toKotlin()
    }

    func open(key: KotlinByteArray, nonce: KotlinByteArray, sealed: KotlinByteArray) -> KotlinByteArray? {
        let body = sealed.toData()
        guard body.count >= 16 else { return nil }
        let ciphertext = body.subdata(in: 0..<(body.count - 16))
        let tag = body.subdata(in: (body.count - 16)..<body.count)
        guard
            let box = try? AES.GCM.SealedBox(
                nonce: try AES.GCM.Nonce(data: nonce.toData()),
                ciphertext: ciphertext,
                tag: tag
            ),
            let opened = try? AES.GCM.open(box, using: SymmetricKey(data: key.toData()))
        else {
            return nil
        }
        return opened.toKotlin()
    }
}

private extension Data {
    func toKotlin() -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        for (i, byte) in enumerated() { array.set(index: Int32(i), value: Int8(bitPattern: byte)) }
        return array
    }
}

extension KotlinByteArray {
    func toData() -> Data {
        var data = Data(capacity: Int(size))
        for i in 0..<size { data.append(UInt8(bitPattern: get(index: i))) }
        return data
    }
}
