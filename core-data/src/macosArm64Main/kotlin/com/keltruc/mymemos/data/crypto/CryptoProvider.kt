package com.keltruc.mymemos.data.crypto

/**
 * AES-256-GCM and PBKDF2-HMAC-SHA256, supplied by the host application.
 *
 * Kotlin/Native's CommonCrypto bindings expose PBKDF2 but no AES-GCM at all: the one-shot
 * entry points are not in the headers it generates from. Rather than reimplement a cipher,
 * the app hands these in from Swift, where CryptoKit does it properly and where the crypto
 * parity spike already proved the bytes match Android's.
 */
interface CryptoProvider {
    fun randomBytes(size: Int): ByteArray

    /** The password arrives as a string because that is what crosses to Swift cleanly. */
    fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray

    fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray

    /** Null when the tag does not check out, which is what a wrong password looks like. */
    fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray?
}

/** Set once at startup, before anything tries to open a locked memo. */
object MacCrypto {
    var provider: CryptoProvider? = null
}
