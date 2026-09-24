package com.keltruc.mymemos.data.crypto

import kotlin.io.encoding.Base64

/**
 * End-to-end encryption of a memo's text with a password the server never sees. The stored
 * form is a single line the web UI shows as an obvious locked blob:
 *
 *     mymemos-enc:v1:<base64(salt16 || nonce12 || AES-256-GCM ciphertext+tag)>
 *
 * AES-256-GCM, key from PBKDF2-HMAC-SHA256 (120k rounds, fresh salt per memo). Tags,
 * search and task toggles are unavailable while a memo is locked; that is the point.
 */
object MemoCipher {
    const val PREFIX = "mymemos-enc:v1:"
    private const val ITERATIONS = 120_000

    class WrongPassword : Exception("Wrong password")

    fun isEncrypted(content: String): Boolean = content.trimStart().startsWith(PREFIX)

    fun encrypt(plain: String, password: CharArray): String {
        val salt = AesGcm.randomBytes(16)
        val nonce = AesGcm.randomBytes(12)
        val key = AesGcm.deriveKey(password, salt, ITERATIONS)
        val body = AesGcm.seal(key, nonce, plain.encodeToByteArray())
        return PREFIX + Base64.Default.encode(salt + nonce + body)
    }

    /**
     * The locked text with [title] beside it in the clear, or with no title if it is blank.
     * Whatever followed the blob before is replaced. See [com.keltruc.mymemos.model.Memo.lockedTitleOf].
     */
    fun withTitle(content: String, title: String?): String {
        val blob = content.trim().lineSequence().first()
        // One line, and no leading hashes, so it reads back as exactly one heading.
        val clean = title?.lineSequence()?.joinToString(" ") { it.trim() }?.trimStart('#')?.trim().orEmpty()
        return if (clean.isEmpty()) blob else "$blob\n\n# $clean"
    }

    fun decrypt(content: String, password: CharArray): String {
        val blob = runCatching {
            Base64.Default.decode(content.trim().lineSequence().first().removePrefix(PREFIX))
        }.getOrNull() ?: throw WrongPassword()
        if (blob.size < 29) throw WrongPassword()
        val salt = blob.copyOfRange(0, 16)
        val nonce = blob.copyOfRange(16, 28)
        val body = blob.copyOfRange(28, blob.size)
        val key = AesGcm.deriveKey(password, salt, ITERATIONS)
        val opened = AesGcm.open(key, nonce, body) ?: throw WrongPassword()
        return opened.decodeToString()
    }
}
