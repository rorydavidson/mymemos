package com.keltruc.mymemos.data.text

/**
 * The task lines in a memo, and how to tick one.
 *
 * Shared rather than written per platform: this edits the memo's own text, and two
 * implementations that disagreed about what counts as a task line, or about which
 * characters to preserve around it, would corrupt content differently on each.
 */
object TaskLine {

    /** An unticked task: `- [ ] text`, `* [ ] text`, `1. [ ] text`. */
    private val open = Regex("""^(\s*)(?:[-*+]|\d+[.)]) \[ \] (.*)$""")

    /** Either state, keeping the marker and anything trailing so ticking preserves them. */
    private val any = Regex("""^(\s*(?:[-*+]|\d+[.)]) )\[([ xX])\](.*)$""")

    data class Open(val lineIndex: Int, val text: String)

    /** Every unticked task in [content], with the line it sits on. */
    fun openTasks(content: String): List<Open> =
        content.lines().mapIndexedNotNull { index, line ->
            open.matchEntire(line)?.let { Open(index, it.groupValues[2]) }
        }

    /** Flips the checkbox on [lineIndex] and returns the new content, or null if not a task line. */
    fun toggle(content: String, lineIndex: Int, checked: Boolean): String? {
        val lines = content.lines().toMutableList()
        val line = lines.getOrNull(lineIndex) ?: return null
        val match = any.matchEntire(line) ?: return null
        lines[lineIndex] = "${match.groupValues[1]}[${if (checked) "x" else " "}]${match.groupValues[3]}"
        return lines.joinToString("\n")
    }
}
