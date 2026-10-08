package com.keltruc.mymemos.web

import com.keltruc.mymemos.data.crypto.MemoCipher
import com.keltruc.mymemos.data.export.BackupCipher
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The vectors were made by the JDK's PBKDF2WithHmacSHA256 and AES/GCM/NoPadding, the
 * primitives the Android app uses, with a fixed salt and nonce. If WebCrypto opens them, a
 * memo locked on a phone opens in a browser.
 */
class WebCipherTest {
    private val password = "correct horse ✓"
    private val plain = "Locked on a phone — opened in a browser. ✓"

    @Test
    fun opensAMemoTheJdkLocked() = runTest {
        val blob = MemoCipher.PREFIX + "AQgPFh0kKzI5QEdOVVxjasjFwr+8ubazsK2qp39I7P6IAShHmEr8sa+wnvd3aFvtcW9m9ynzIHPvF23/HTR1cfQ4CRNUpb1lrMPOE0t7e/jnH8n7DiED8tD6"
        assertEquals(plain, WebCipher.decryptMemo("$blob\n\n# Car insurance", password))
    }

    @Test
    fun opensABackupTheJdkSealed() = runTest {
        val body = Base64.Default.decode("AQgPFh0kKzI5QEdOVVxjasjFwr+8ubazsK2qp8DPZrFTcGTP5WmoSzZYjhuhHl09BTYVRKCISZLZXj1k3yFMY4NKtX7pAcP06+13IBJ41JRIGZzXzqFL1yWE")
        val blob = BackupCipher.MAGIC.encodeToByteArray() + body
        assertEquals(plain, WebCipher.decryptBackup(blob, password).decodeToString())
    }

    @Test
    fun roundTripsAndRefusesTheWrongPassword() = runTest {
        val locked = WebCipher.encryptMemo("# Private\n- [ ] thing", "pw")
        assertTrue(MemoCipher.isEncrypted(locked))
        assertEquals("# Private\n- [ ] thing", WebCipher.decryptMemo(locked, "pw"))
        assertFailsWith<MemoCipher.WrongPassword> { WebCipher.decryptMemo(locked, "nope") }
    }
}
