package com.keltruc.mymemos.data.text

/**
 * The one line that stands for a memo in a list: its first heading if it has one, otherwise its
 * first line with any Markdown decoration taken off.
 */
object MemoTitle {
    private val heading = Regex("^#{1,6}\\s+(.+)$")
    private val listMarker = Regex("^\\s*(?:[-*+]|\\d{1,9}[.)])\\s+(?:\\[[ xX]]\\s*)?")
    // Bold, italic, code and strikethrough runs, so a title does not arrive full of asterisks.
    private val emphasis = Regex("(\\*\\*|__|\\*|_|`|~~)")
    private val link = Regex("\\[([^]]*)]\\([^)]*\\)")

    /** Null when there is nothing worth showing; the caller supplies its own wording for that. */
    fun of(content: String): String? {
        val lines = ColourTag.strip(content).lineSequence()
        val firstHeading = lines.firstNotNullOfOrNull { heading.find(it.trim())?.groupValues?.get(1) }
        val raw = firstHeading ?: ColourTag.strip(content).lineSequence().firstOrNull { it.isNotBlank() }
        return raw?.let(::tidy)?.takeIf { it.isNotBlank() }
    }

    // The list marker has to go before the trim: trimming "- " first leaves a bare "-", which no
    // longer looks like a marker and would be offered as the title.
    private fun tidy(line: String): String =
        line.replace(listMarker, "")
            .let { link.replace(it) { m -> m.groupValues[1] } }
            .replace(emphasis, "")
            .trim()
}
