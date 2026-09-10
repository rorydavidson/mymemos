package com.keltruc.mymemos.data.text

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A view that shows a memo's title above its text needs to know which line the title came
 * from, or it prints it twice. Getting this wrong in the other direction is worse: it would
 * silently swallow the first line of someone's writing.
 */
class MemoTitleBodyTest {

    @Test
    fun `a leading heading is dropped`() {
        assertEquals("- milk", MemoTitle.withoutTitleLine("# Shopping\n\n- milk"))
    }

    @Test
    fun `a bold first line is dropped because that is where the title came from`() {
        assertEquals("body", MemoTitle.withoutTitleLine("**Wed, 9th Sept**\n\nbody"))
    }

    @Test
    fun `a plain first line is dropped too`() {
        assertEquals("and more", MemoTitle.withoutTitleLine("just a note\nand more"))
    }

    @Test
    fun `a heading further down is not the first line so nothing is dropped`() {
        // The title comes from the heading, but the first line is something else and keeping
        // it matters more than avoiding a repeat.
        val content = "a preamble\n\n# The heading\n\nbody"
        assertEquals(content, MemoTitle.withoutTitleLine(content))
    }

    @Test
    fun `only the blank run after the title is eaten`() {
        assertEquals("first\n\nsecond", MemoTitle.withoutTitleLine("# T\n\n\nfirst\n\nsecond"))
    }

    @Test
    fun `content with nothing worth titling is untouched`() {
        assertEquals("", MemoTitle.withoutTitleLine(""))
        assertEquals("   ", MemoTitle.withoutTitleLine("   "))
    }

    @Test
    fun `a one line memo leaves nothing behind`() {
        assertEquals("", MemoTitle.withoutTitleLine("# Only a heading"))
    }
}
