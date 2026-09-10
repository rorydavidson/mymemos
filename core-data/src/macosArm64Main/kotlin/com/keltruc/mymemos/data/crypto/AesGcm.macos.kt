package com.keltruc.mymemos.data.crypto

/**
 * Delegates to whatever the host application supplied. Failing outright when nothing has is
 * the only safe behaviour: anything that guessed, padded or silently returned the ciphertext
 * would corrupt a memo the moment it was saved back.
 */
internal actual object AesGcm {

    private val provider: CryptoProvider
        get() = MacCrypto.provider
            ?: throw IllegalStateException("No CryptoProvider set; locked memos cannot be opened.")

    actual fun randomBytes(size: Int): ByteArray = provider.randomBytes(size)

    actual fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray =
        provider.deriveKey(password.concatToString(), salt, iterations, keyBits / 8)

    actual fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray =
        provider.seal(key, nonce, plaintext)

    actual fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray? =
        provider.open(key, nonce, sealed)
}
