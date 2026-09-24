package com.keltruc.mymemos.data.text

import com.keltruc.mymemos.model.NoteColour

/**
 * Note colour travels between devices as a tag on its own last line, `#colour/yellow`.
 * The server treats it as an ordinary tag (so the web UI can even filter on it); the app
 * hides it from the rendered text and the tag chips and shows the tint instead.
 */
object ColourTag {
    const val PREFIX = "colour/"
    private val line = Regex("(?m)^[ \\t]*#colour/([a-z]+)[ \\t]*$\\n?")

    fun extract(content: String): NoteColour? =
        line.find(content)?.groupValues?.get(1)?.let { name -> NoteColour.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

    fun strip(content: String): String = line.replace(content, "").trimEnd('\n')

    fun apply(content: String, colour: NoteColour?): String {
        val base = strip(content)
        return if (colour == null) base else base.trimEnd() + "\n\n#colour/" + colour.name.lowercase()
    }

    fun isColourTag(tag: String): Boolean = tag.startsWith(PREFIX)

    /**
     * One memo's tags without the colour. The server reports a nested tag's parents too, so
     * `#colour/yellow` arrives as both `colour/yellow` and `colour`. The bare `colour` only
     * goes when a `colour/x` sits beside it, so a memo genuinely tagged `#colour` keeps it.
     */
    fun visible(tags: List<String>): List<String> {
        val tinted = tags.any(::isColourTag)
        return tags.filter { !isColourTag(it) && !(tinted && it == PREFIX.trimEnd('/')) }
    }
}
