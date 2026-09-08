package com.keltruc.mymemos.data.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoTest {
    private val payload = ByteArray(50_000) { (it % 251).toByte() }

    private fun encrypt(pw: String): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypting(out, pw.toCharArray()).use { it.write(payload) }
        return out.toByteArray()
    }

    @Test
    fun `round trip with the right password`() {
        val blob = encrypt("correct horse")
        val back = BackupCrypto.decrypting(ByteArrayInputStream(blob), "correct horse".toCharArray()).readBytes()
        assertArrayEquals(payload, back)
    }

    @Test
    fun `wrong password fails rather than returning garbage`() {
        val blob = encrypt("correct horse")
        assertThrows(Exception::class.java) {
            BackupCrypto.decrypting(ByteArrayInputStream(blob), "battery staple".toCharArray()).readBytes()
        }
    }

    @Test
    fun `tampered bytes are rejected`() {
        val blob = encrypt("pw")
        blob[blob.size / 2] = (blob[blob.size / 2] + 1).toByte()
        assertThrows(Exception::class.java) {
            BackupCrypto.decrypting(ByteArrayInputStream(blob), "pw".toCharArray()).readBytes()
        }
    }

    @Test
    fun `not a backup file`() {
        assertThrows(BackupCrypto.WrongPasswordOrCorrupt::class.java) {
            BackupCrypto.decrypting(ByteArrayInputStream("hello world, not a backup".toByteArray()), "pw".toCharArray())
        }
    }
}
