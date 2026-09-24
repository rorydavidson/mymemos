package com.keltruc.mymemos.data.text

import com.keltruc.mymemos.model.NoteColour
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class ColourTagTest {
    @Test
    fun `apply appends a colour line and extract reads it back`() {
        val tagged = ColourTag.apply("Buy milk #home", NoteColour.YELLOW)
        assertEquals("Buy milk #home\n\n#colour/yellow", tagged)
        assertEquals(NoteColour.YELLOW, ColourTag.extract(tagged))
        assertEquals("Buy milk #home", ColourTag.strip(tagged))
    }

    @Test
    fun `changing colour replaces rather than stacks`() {
        val once = ColourTag.apply("note", NoteColour.RED)
        val twice = ColourTag.apply(once, NoteColour.BLUE)
        assertEquals("note\n\n#colour/blue", twice)
        assertEquals(NoteColour.BLUE, ColourTag.extract(twice))
    }

    @Test
    fun `clearing removes the line`() {
        assertEquals(ColourTag.apply(ColourTag.apply("note", NoteColour.RED), null), "note")
    }

    @Test
    fun `an inline mention is not a colour tag`() {
        val text = "my favourite #colour/red thing"
        assertNull(ColourTag.extract(text))
        assertEquals(text, ColourTag.strip(text))
    }

    @Test
    fun `the parent tag the server adds goes with the colour`() {
        assertEquals(listOf("session"), ColourTag.visible(listOf("session", "colour", "colour/lime")))
    }

    @Test
    fun `a bare colour tag with no tint beside it stays`() {
        assertEquals(listOf("colour", "paint"), ColourTag.visible(listOf("colour", "paint")))
    }
}
