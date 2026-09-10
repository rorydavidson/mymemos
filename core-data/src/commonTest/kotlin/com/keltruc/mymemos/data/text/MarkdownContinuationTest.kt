package com.keltruc.mymemos.data.text

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class MarkdownContinuationTest {

    /** Types a return at the end of [before] and returns what the editor would end up showing. */
    private fun pressReturn(before: String): String {
        val text = "$before\n"
        val result = MarkdownContinuation.continueList(text, text.length) ?: return text
        return result.text.substring(0, result.cursor) + "|" + result.text.substring(result.cursor)
    }

    @Test
    fun `bullet carries onto the next line`() {
        assertEquals("- milk\n- |", pressReturn("- milk"))
        assertEquals("* milk\n* |", pressReturn("* milk"))
        assertEquals("+ milk\n+ |", pressReturn("+ milk"))
    }

    @Test
    fun `task carries onto the next line unticked`() {
        assertEquals("- [ ] milk\n- [ ] |", pressReturn("- [ ] milk"))
        assertEquals("- [x] milk\n- [ ] |", pressReturn("- [x] milk"))
    }

    @Test
    fun `numbered item increments`() {
        assertEquals("1. milk\n2. |", pressReturn("1. milk"))
        assertEquals("9) milk\n10) |", pressReturn("9) milk"))
    }

    @Test
    fun `indentation is preserved`() {
        assertEquals("  - milk\n  - |", pressReturn("  - milk"))
        assertEquals("\t- [ ] milk\n\t- [ ] |", pressReturn("\t- [ ] milk"))
    }

    @Test
    fun `return on an empty marker clears the line`() {
        assertEquals("- milk\n|", pressReturn("- milk\n- "))
        assertEquals("- milk\n|", pressReturn("- milk\n- [ ] "))
        assertEquals("- milk\n|", pressReturn("- milk\n- [ ]"))
        assertEquals("1. milk\n|", pressReturn("1. milk\n2. "))
    }

    @Test
    fun `an empty marker on the first line clears to an empty document`() {
        assertEquals("|", pressReturn("- "))
    }

    @Test
    fun `prose and headings are left alone`() {
        assertNull(MarkdownContinuation.continueList("just typing\n", 12))
        assertNull(MarkdownContinuation.continueList("# Heading\n", 10))
        assertNull(MarkdownContinuation.continueList("-no space\n", 10))
        assertNull(MarkdownContinuation.continueList("\n", 1))
    }

    @Test
    fun `carries on mid document without disturbing what follows`() {
        val text = "- one\n- two\nprose"
        val cursor = 6 // just after the newline that ends "- one"
        val result = MarkdownContinuation.continueList(text, cursor)!!
        assertEquals("- one\n- - two\nprose", result.text)
        assertEquals(8, result.cursor)
    }

    @Test
    fun `a return typed at the end of an item continues it`() {
        val result = MarkdownContinuation.continueAfterReturn("- milk", 6, "- milk\n", 7)!!
        assertEquals("- milk\n- ", result.text)
        assertEquals(9, result.cursor)
    }

    @Test
    fun `the keyboard may recase the line it commits with the return`() {
        // Real behaviour: typing "1. first" then return arrives as "1. First\n", because the IME
        // capitalises after the full stop at the moment it commits.
        val result = MarkdownContinuation.continueAfterReturn("1. first", 8, "1. First\n", 9)!!
        assertEquals("1. First\n2. ", result.text)
        assertEquals(12, result.cursor)
    }

    @Test
    fun `edits that are not a single return are left alone`() {
        // Pasting a line, rather than typing a return.
        assertNull(MarkdownContinuation.continueAfterReturn("- milk", 6, "- milk- eggs\n", 13))
        // A plain character.
        assertNull(MarkdownContinuation.continueAfterReturn("- milk", 6, "- milks", 7))
        // A deletion.
        assertNull(MarkdownContinuation.continueAfterReturn("- milk", 6, "- mil", 5))
        // A return whose surrounding text moved, so it was not simply typed at the cursor.
        assertNull(MarkdownContinuation.continueAfterReturn("- milk", 6, "- milk\nx", 7))
    }

    @Test
    fun `a return in the middle of an item still continues it`() {
        val result = MarkdownContinuation.continueAfterReturn("- milk", 3, "- m\nilk", 4)!!
        assertEquals("- m\n- ilk", result.text)
        assertEquals(6, result.cursor)
    }

    @Test
    fun `only acts on a cursor sitting after a newline`() {
        assertNull(MarkdownContinuation.continueList("- milk", 6))
        assertNull(MarkdownContinuation.continueList("- milk\n", 0))
        assertNull(MarkdownContinuation.continueList("- milk\n", 99))
    }
}
