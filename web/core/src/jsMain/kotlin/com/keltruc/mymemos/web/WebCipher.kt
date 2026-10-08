package com.keltruc.mymemos.web

import com.keltruc.mymemos.data.crypto.AesGcm
import com.keltruc.mymemos.data.crypto.MemoCipher
import com.keltruc.mymemos.data.export.BackupCipher
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.io.encoding.Base64
import kotlin.js.Promise

/**
 * MemoCipher and BackupCipher, with the primitives done by the browser's WebCrypto, which
 * only works asynchronously. The layouts, prefixes and round counts are read from the shared
 * objects rather than restated, so a memo locked here opens on a phone and the reverse.
 *
 * WebCrypto only exists in a secure context: HTTPS, or localhost while developing.
 */
object WebCipher {

    suspend fun encryptMemo(plain: String, password: String): String {
        val salt = AesGcm.randomBytes(16)
        val nonce = AesGcm.randomBytes(12)
        val key = deriveKey(password, salt, MemoCipher.ITERATIONS)
        val body = seal(key, nonce, plain.encodeToByteArray())
        return MemoCipher.PREFIX + Base64.Default.encode(salt + nonce + body)
    }

    /** Throws [MemoCipher.WrongPassword] for a wrong password or a damaged blob. */
    suspend fun decryptMemo(content: String, password: String): String {
        val blob = runCatching {
            Base64.Default.decode(content.trim().lineSequence().first().removePrefix(MemoCipher.PREFIX))
        }.getOrNull() ?: throw MemoCipher.WrongPassword()
        if (blob.size < 29) throw MemoCipher.WrongPassword()
        val key = deriveKey(password, blob.copyOfRange(0, 16), MemoCipher.ITERATIONS)
        val opened = open(key, blob.copyOfRange(16, 28), blob.copyOfRange(28, blob.size)) ?: throw MemoCipher.WrongPassword()
        return opened.decodeToString()
    }

    suspend fun encryptBackup(plain: ByteArray, password: String): ByteArray {
        val salt = AesGcm.randomBytes(BackupCipher.SALT)
        val nonce = AesGcm.randomBytes(BackupCipher.NONCE)
        val key = deriveKey(password, salt, BackupCipher.ITERATIONS)
        return BackupCipher.MAGIC.encodeToByteArray() + salt + nonce + seal(key, nonce, plain)
    }

    suspend fun decryptBackup(blob: ByteArray, password: String): ByteArray {
        val magic = BackupCipher.MAGIC.length
        val header = magic + BackupCipher.SALT + BackupCipher.NONCE
        if (blob.size < header + 16 || blob.copyOfRange(0, magic).decodeToString() != BackupCipher.MAGIC) throw BackupCipher.WrongPasswordOrCorrupt()
        val key = deriveKey(password, blob.copyOfRange(magic, magic + BackupCipher.SALT), BackupCipher.ITERATIONS)
        return open(key, blob.copyOfRange(magic + BackupCipher.SALT, header), blob.copyOfRange(header, blob.size))
            ?: throw BackupCipher.WrongPasswordOrCorrupt()
    }

    // ---- WebCrypto ------------------------------------------------------------------------

    private val subtle: dynamic get() = js("globalThis.crypto.subtle")

    /** PBKDF2-HMAC-SHA256 over the password's UTF-8 bytes, as the JDK and CommonCrypto do. */
    private suspend fun deriveKey(password: String, salt: ByteArray, iterations: Int): dynamic {
        val material = (subtle.importKey("raw", password.encodeToByteArray().toUint8(), "PBKDF2", false, arrayOf("deriveKey")) as Promise<dynamic>).await()
        val params: dynamic = js("({})")
        params.name = "PBKDF2"
        params.salt = salt.toUint8()
        params.iterations = iterations
        params.hash = "SHA-256"
        val aes: dynamic = js("({})")
        aes.name = "AES-GCM"
        aes.length = 256
        return (subtle.deriveKey(params, material, aes, false, arrayOf("encrypt", "decrypt")) as Promise<dynamic>).await()
    }

    private fun gcm(nonce: ByteArray): dynamic {
        val p: dynamic = js("({})")
        p.name = "AES-GCM"
        p.iv = nonce.toUint8()
        p.tagLength = 128
        return p
    }

    private suspend fun seal(key: dynamic, nonce: ByteArray, plain: ByteArray): ByteArray =
        (subtle.encrypt(gcm(nonce), key, plain.toUint8()) as Promise<ArrayBuffer>).await().toByteArray()

    /** Null when the tag does not check out, which is what a wrong password looks like. */
    private suspend fun open(key: dynamic, nonce: ByteArray, sealed: ByteArray): ByteArray? =
        runCatching { (subtle.decrypt(gcm(nonce), key, sealed.toUint8()) as Promise<ArrayBuffer>).await().toByteArray() }.getOrNull()
}

internal fun ByteArray.toUint8(): Uint8Array {
    val i8 = unsafeCast<Int8Array>()
    return Uint8Array(i8.buffer, i8.byteOffset, i8.length)
}

internal fun ArrayBuffer.toByteArray(): ByteArray = Int8Array(this).unsafeCast<ByteArray>()

internal fun Uint8Array.toByteArray(): ByteArray = Int8Array(buffer, byteOffset, length).unsafeCast<ByteArray>().copyOf()
