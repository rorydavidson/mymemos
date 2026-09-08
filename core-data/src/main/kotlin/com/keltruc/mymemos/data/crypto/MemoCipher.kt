package com.keltruc.mymemos.data.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(salt + nonce + body)
    }

    fun decrypt(content: String, password: CharArray): String {
        val blob = runCatching { Base64.getDecoder().decode(content.trim().removePrefix(PREFIX)) }.getOrNull()
            ?: throw WrongPassword()
        if (blob.size < 29) throw WrongPassword()
        val salt = blob.copyOfRange(0, 16)
        val nonce = blob.copyOfRange(16, 28)
        val body = blob.copyOfRange(28, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return try {
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw WrongPassword()
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password, salt, ITERATIONS, 256)).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
