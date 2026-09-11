package com.keltruc.mymemos.data.export

import com.keltruc.mymemos.data.crypto.AesGcm

/**
 * The backup file's encryption, done in one piece: 8-byte magic, 16-byte salt, 12-byte
 * nonce, then the ciphertext with the GCM tag at the end, PBKDF2-HMAC-SHA256 at 200k rounds.
 * Byte for byte the format Android's streaming BackupCrypto writes, so a backup made on a
 * phone restores on a Mac and the other way round. Held in memory because CryptoKit has no
 * streaming GCM; a backup is the database plus attachments, which fits.
 */
object BackupCipher {
    const val MAGIC = "MYMEMOS1"
    private const val ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private const val SALT = 16
    private const val NONCE = 12

    class WrongPasswordOrCorrupt : Exception("Wrong password or damaged backup")

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = AesGcm.randomBytes(SALT)
        val nonce = AesGcm.randomBytes(NONCE)
        val key = AesGcm.deriveKey(password, salt, ITERATIONS, KEY_BITS)
        return MAGIC.encodeToByteArray() + salt + nonce + AesGcm.seal(key, nonce, plain)
    }

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        val header = MAGIC.length + SALT + NONCE
        if (blob.size < header + 16 || blob.copyOfRange(0, MAGIC.length).decodeToString() != MAGIC) throw WrongPasswordOrCorrupt()
        val salt = blob.copyOfRange(MAGIC.length, MAGIC.length + SALT)
        val nonce = blob.copyOfRange(MAGIC.length + SALT, header)
        val key = AesGcm.deriveKey(password, salt, ITERATIONS, KEY_BITS)
        return AesGcm.open(key, nonce, blob.copyOfRange(header, blob.size)) ?: throw WrongPasswordOrCorrupt()
    }
}
