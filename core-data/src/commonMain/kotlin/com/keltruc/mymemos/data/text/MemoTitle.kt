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

    /**
     * The content without the line [of] took the title from, for a view that shows the title
     * separately and would otherwise print it twice.
     *
     * The comparison is on the tidied line rather than the raw one, so a first line of
     * `**Wednesday**` or `## Wednesday` is recognised as the title it produced. A memo whose
     * title came from somewhere other than the first line is left alone.
     */
    fun withoutTitleLine(content: String): String {
        val title = of(content) ?: return content
        val lines = content.lines()
        val first = lines.indexOfFirst { it.isNotBlank() }
        if (first < 0 || tidy(lines[first].removeHeadingMarker()) != title) return content
        return lines.drop(first + 1).dropWhile { it.isBlank() }.joinToString("\n")
    }

    private fun String.removeHeadingMarker(): String =
        heading.find(trim())?.groupValues?.get(1) ?: this

    // The list marker has to go before the trim: trimming "- " first leaves a bare "-", which no
    // longer looks like a marker and would be offered as the title.
    private fun tidy(line: String): String =
        line.replace(listMarker, "")
            .let { link.replace(it) { m -> m.groupValues[1] } }
            .replace(emphasis, "")
            .trim()
}
