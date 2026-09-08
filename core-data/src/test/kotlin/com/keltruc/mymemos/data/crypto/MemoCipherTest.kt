package com.keltruc.mymemos.data.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoCipherTest {
    private val text = "# Private\n- [ ] see the doctor #health\n\nsome unicode: ünïcødé"

    @Test
    fun `round trip`() {
        val locked = MemoCipher.encrypt(text, "hunter2".toCharArray())
        assertTrue(MemoCipher.isEncrypted(locked))
        assertFalse(locked.contains("doctor"))
        assertEquals(text, MemoCipher.decrypt(locked, "hunter2".toCharArray()))
    }

    @Test
    fun `wrong password is reported, not garbage`() {
        val locked = MemoCipher.encrypt(text, "hunter2".toCharArray())
        assertThrows(MemoCipher.WrongPassword::class.java) { MemoCipher.decrypt(locked, "hunter3".toCharArray()) }
    }

    @Test
    fun `same text encrypts differently each time`() {
        assertNotEquals(MemoCipher.encrypt(text, "pw".toCharArray()), MemoCipher.encrypt(text, "pw".toCharArray()))
    }

    @Test
    fun `plain text is not mistaken for a locked memo`() {
        assertFalse(MemoCipher.isEncrypted("just a note about locks"))
        assertThrows(MemoCipher.WrongPassword::class.java) { MemoCipher.decrypt("mymemos-enc:v1:notbase64!!", "pw".toCharArray()) }
    }
}
