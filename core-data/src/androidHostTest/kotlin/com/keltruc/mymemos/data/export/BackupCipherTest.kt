package com.keltruc.mymemos.data.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The one-shot cipher the Apple apps use against the streaming one Android uses. Both must
 * read what the other wrote, or a backup stops being portable between devices.
 */
class BackupCipherTest {
    private val payload = ByteArray(50_000) { (it % 251).toByte() }

    @Test
    fun `one-shot reads what streaming wrote`() {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypting(out, "correct horse".toCharArray()).use { it.write(payload) }
        assertArrayEquals(payload, BackupCipher.decrypt(out.toByteArray(), "correct horse".toCharArray()))
    }

    @Test
    fun `streaming reads what one-shot wrote`() {
        val blob = BackupCipher.encrypt(payload, "correct horse".toCharArray())
        val back = BackupCrypto.decrypting(ByteArrayInputStream(blob), "correct horse".toCharArray()).readBytes()
        assertArrayEquals(payload, back)
    }

    @Test
    fun `wrong password and tampering are refused`() {
        val blob = BackupCipher.encrypt(payload, "pw".toCharArray())
        assertThrows(BackupCipher.WrongPasswordOrCorrupt::class.java) { BackupCipher.decrypt(blob, "other".toCharArray()) }
        blob[blob.size / 2] = (blob[blob.size / 2] + 1).toByte()
        assertThrows(BackupCipher.WrongPasswordOrCorrupt::class.java) { BackupCipher.decrypt(blob, "pw".toCharArray()) }
        assertThrows(BackupCipher.WrongPasswordOrCorrupt::class.java) { BackupCipher.decrypt("not a backup".toByteArray(), "pw".toCharArray()) }
    }
}
