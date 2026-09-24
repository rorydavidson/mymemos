package com.keltruc.mymemos.data.crypto

import com.keltruc.mymemos.model.Memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LockedTitleTest {
    private val blob = MemoCipher.PREFIX + "c2FsdG5vbmNlY2lwaGVy"

    @Test
    fun `a title sits after the blob and reads back`() {
        val titled = MemoCipher.withTitle(blob, "Car insurance")
        assertEquals("$blob\n\n# Car insurance", titled)
        assertEquals("Car insurance", Memo.lockedTitleOf(titled))
        assertTrue(MemoCipher.isEncrypted(titled))
    }

    @Test
    fun `renaming replaces the title and a blank one removes it`() {
        val once = MemoCipher.withTitle(blob, "First")
        assertEquals("$blob\n\n# Second", MemoCipher.withTitle(once, "Second"))
        assertEquals(blob, MemoCipher.withTitle(once, "  "))
        assertNull(Memo.lockedTitleOf(blob))
    }

    @Test
    fun `a title is kept to one plain heading`() {
        assertEquals("$blob\n\n# Two lines", MemoCipher.withTitle(blob, "## Two\nlines"))
    }

    @Test
    fun `the first line alone is what gets decrypted`() {
        val titled = MemoCipher.withTitle(blob, "Anything")
        assertEquals(blob, titled.trim().lineSequence().first())
    }
}
