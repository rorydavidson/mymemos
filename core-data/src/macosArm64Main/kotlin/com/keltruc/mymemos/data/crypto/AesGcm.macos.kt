package com.keltruc.mymemos.data.crypto

/**
 * Not yet implemented on macOS, and deliberately loud about it.
 *
 * The crypto-parity spike proved CryptoKit and CommonCrypto produce the same bytes as the
 * Android implementation, so this is a wiring job rather than an open question. Until it is
 * wired, a locked memo cannot be opened here. Failing outright is the only safe placeholder:
 * anything that guessed, padded or silently returned the ciphertext would corrupt a memo the
 * moment it was saved back.
 */
internal actual object AesGcm {

    actual fun randomBytes(size: Int): ByteArray = unsupported()

    actual fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray = unsupported()

    actual fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray = unsupported()

    actual fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray? = unsupported()

    private fun unsupported(): Nothing =
        throw NotImplementedError("Locked memos are not supported on macOS yet: AesGcm needs wiring to CryptoKit.")
}
