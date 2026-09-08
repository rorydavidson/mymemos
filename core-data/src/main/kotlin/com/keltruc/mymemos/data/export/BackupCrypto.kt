package com.keltruc.mymemos.data.export

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based AES-256-GCM stream. Layout: 8-byte magic, 16-byte salt, 12-byte nonce,
 * then ciphertext with the GCM tag at the end. PBKDF2-HMAC-SHA256 with 200k rounds.
 */
object BackupCrypto {
    private const val MAGIC = "MYMEMOS1"
    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256

    class WrongPasswordOrCorrupt : Exception("Wrong password or damaged backup")

    fun encrypting(out: OutputStream, password: CharArray): OutputStream {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(salt)
        out.write(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return CipherOutputStream(out, cipher)
    }

    fun decrypting(input: InputStream, password: CharArray): InputStream {
        val magic = ByteArray(8)
        if (input.read(magic) != 8 || String(magic, Charsets.US_ASCII) != MAGIC) throw WrongPasswordOrCorrupt()
        val salt = ByteArray(16).also { if (input.read(it) != 16) throw WrongPasswordOrCorrupt() }
        val nonce = ByteArray(12).also { if (input.read(it) != 12) throw WrongPasswordOrCorrupt() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return CipherInputStream(input, cipher)
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
