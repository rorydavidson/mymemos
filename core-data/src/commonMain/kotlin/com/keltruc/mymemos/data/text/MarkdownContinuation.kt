package com.keltruc.mymemos.data.text

/**
 * Carries a list marker onto the next line when the user presses return inside a bullet, task or
 * numbered item, and clears the marker when they press return on an item they never filled in.
 *
 * The editor calls [continueList] after the newline has already landed in the text, so the input
 * is the text as the field now holds it plus the cursor position just past that newline.
 */
object MarkdownContinuation {

    /** Text and cursor the editor should show instead of what the user's keystroke produced. */
    data class Result(val text: String, val cursor: Int)

    // "- ", "* item", "+ [x] done". The box's trailing space is optional so an untouched "- [ ]"
    // still reads as an empty task rather than a bullet whose text happens to be "[ ]".
    private val bullet = Regex("""^([ \t]*)([-*+]) (\[[ xX]\] ?)?""")
    // Digits are capped so a silly long "number" cannot overflow the increment below.
    private val ordered = Regex("""^([ \t]*)(\d{1,9})([.)]) """)

    /**
     * The editor's entry point: given the field before and after a change, returns a replacement
     * when that change was a return typed at the end of a list item.
     *
     * The check deliberately tolerates the keyboard rewriting the line it just committed. Typing
     * "1. first" and pressing return arrives as "1. First\n", because the IME capitalises after
     * the full stop as it commits, so comparing the text before the cursor would miss it. What
     * must hold is that the cursor moved on by one, the text grew by one, that one character is
     * the newline, and everything after the cursor is untouched.
     */
    fun continueAfterReturn(beforeText: String, beforeCursor: Int, afterText: String, afterCursor: Int): Result? {
        if (afterCursor != beforeCursor + 1) return null
        if (afterText.length != beforeText.length + 1) return null
        if (afterText.getOrNull(afterCursor - 1) != '\n') return null
        if (beforeCursor !in 0..beforeText.length) return null
        if (afterText.substring(afterCursor) != beforeText.substring(beforeCursor)) return null
        return continueList(afterText, afterCursor)
    }

    /**
     * Returns the replacement text and cursor, or null when the line that was just ended is not a
     * list item and the keystroke should stand as typed.
     */
    fun continueList(text: String, cursor: Int): Result? {
        if (cursor !in 1..text.length || text[cursor - 1] != '\n') return null
        val lineStart = text.lastIndexOf('\n', cursor - 2).let { if (it < 0) 0 else it + 1 }
        val line = text.substring(lineStart, cursor - 1)

        val (matched, marker) = markerFor(line) ?: return null

        // Return on an item with nothing in it means "I am done with this list": drop the marker
        // and the newline, leaving the cursor on the now-empty line.
        if (line.substring(matched).isBlank()) return Result(text.removeRange(lineStart, cursor), lineStart)

        return Result(text.substring(0, cursor) + marker + text.substring(cursor), cursor + marker.length)
    }

    /** The length of the line's marker, and the marker the next line should open with. */
    private fun markerFor(line: String): Pair<Int, String>? {
        bullet.find(line)?.let { m ->
            val (indent, glyph, box) = m.destructured
            // A ticked box does not carry its tick over; the new item starts unticked.
            return m.value.length to if (box.isEmpty()) "$indent$glyph " else "$indent$glyph [ ] "
        }
        ordered.find(line)?.let { m ->
            val (indent, number, delimiter) = m.destructured
            return m.value.length to "$indent${number.toInt() + 1}$delimiter "
        }
        return null
    }
}
