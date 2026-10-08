package com.keltruc.mymemos.data.text

/**
 * Moves ticked task lines to the bottom of the task list they belong to. A list is a run of
 * consecutive task lines at the same indent; anything else (blank line, prose, heading) ends
 * the run, so separate lists in one memo are sorted independently. Order within the ticked
 * and unticked groups is preserved.
 */
object TaskListSorter {
    private val task = Regex("^(\\s*)[-*] \\[([ xX])\\] .*$")

    fun sortCompletedToBottom(content: String): String {
        val lines = content.lines()
        val out = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            val m = task.matchEntire(lines[i])
            if (m == null) {
                out += lines[i]
                i++
                continue
            }
            val indent = m.groupValues[1]
            val run = mutableListOf<String>()
            while (i < lines.size) {
                val mm = task.matchEntire(lines[i]) ?: break
                if (mm.groupValues[1] != indent) break
                run += lines[i]
                i++
            }
            val (done, open) = run.partition { task.matchEntire(it)!!.groupValues[2] != " " }
            out += open
            out += done
        }
        return out.joinToString("\n")
    }
}
