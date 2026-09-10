package com.keltruc.mymemos.data.crypto

/**
 * The primitives behind [MemoCipher] and the backup format: AES-256-GCM and PBKDF2-HMAC-SHA256.
 *
 * The layout of a locked memo is shared code; the primitives are not, because every platform
 * ships its own. What matters is that they agree byte for byte, which is checked against fixed
 * vectors rather than assumed: see AndroidCryptoParityTest and the crypto-parity spike.
 */
internal expect object AesGcm {

    fun randomBytes(size: Int): ByteArray

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, keyBits: Int = 256): ByteArray

    fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray

    /** Null when the tag does not check out, which is what a wrong password looks like. */
    fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray?
}
