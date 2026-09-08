package com.keltruc.mymemos.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoTitleTest {

    @Test
    fun `a heading wins wherever it sits`() {
        assertEquals("Shopping list", MemoTitle.of("# Shopping list\n- milk"))
        assertEquals("Shopping list", MemoTitle.of("some preamble\n\n## Shopping list\n- milk"))
    }

    @Test
    fun `without a heading the first non-blank line is used`() {
        assertEquals("call the plumber", MemoTitle.of("\n\ncall the plumber\nand the sparks"))
    }

    @Test
    fun `markdown decoration comes off`() {
        assertEquals("milk", MemoTitle.of("- [ ] milk"))
        assertEquals("milk", MemoTitle.of("1. milk"))
        assertEquals("pay rent", MemoTitle.of("**pay rent**"))
        assertEquals("see the docs", MemoTitle.of("[see the docs](https://example.com)"))
        assertEquals("gone", MemoTitle.of("~~gone~~"))
    }

    @Test
    fun `the hidden colour line is not a title`() {
        assertEquals("real content", MemoTitle.of("#colour/green\nreal content"))
    }

    @Test
    fun `nothing to show gives null`() {
        assertNull(MemoTitle.of(""))
        assertNull(MemoTitle.of("   \n\n  "))
        assertNull(MemoTitle.of("#colour/green"))
        // A bare marker with no text behind it is not a title either.
        assertNull(MemoTitle.of("- "))
    }
}
