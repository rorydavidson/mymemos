package com.keltruc.mymemos.data.crypto

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

/**
 * The browser only does AES-GCM and PBKDF2 asynchronously, through WebCrypto, and these
 * primitives are synchronous on every other platform. So on the web only [randomBytes] is
 * real; locking, unlocking and backups go through com.keltruc.mymemos.web.WebCipher, which
 * keeps MemoCipher's and BackupCipher's layout and constants. Reaching the others is a bug.
 */
internal actual object AesGcm {

    actual fun randomBytes(size: Int): ByteArray {
        val buffer = Uint8Array(size)
        js("globalThis.crypto.getRandomValues(buffer)")
        return ByteArray(size) { buffer[it] }
    }

    actual fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray =
        throw UnsupportedOperationException("Use WebCipher on the web")

    actual fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray =
        throw UnsupportedOperationException("Use WebCipher on the web")

    actual fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray? =
        throw UnsupportedOperationException("Use WebCipher on the web")
}
