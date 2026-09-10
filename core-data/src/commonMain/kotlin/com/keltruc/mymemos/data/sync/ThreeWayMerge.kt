package com.keltruc.mymemos.data.sync

/**
 * Line-based three-way merge. Both sides' edits are expressed as deltas against [base];
 * non-overlapping deltas are applied together, overlapping ones are a conflict.
 */
object ThreeWayMerge {

    sealed interface Result {
        data class Merged(val text: String) : Result
        data object Conflict : Result
    }

    fun merge(base: String, local: String, server: String): Result {
        if (local == server) return Result.Merged(local)
        if (base == local) return Result.Merged(server)
        if (base == server) return Result.Merged(local)

        val baseLines = base.lines()
        val localDeltas = LineDiff.deltas(baseLines, local.lines()).map { Side.LOCAL to it }
        val serverDeltas = LineDiff.deltas(baseLines, server.lines()).map { Side.SERVER to it }
        val all = (localDeltas + serverDeltas).sortedWith(compareBy({ it.second.position }, { it.first }))

        val out = mutableListOf<String>()
        var cursor = 0
        var i = 0
        while (i < all.size) {
            val (side, delta) = all[i]
            val start = delta.position
            val end = delta.endPosition
            val next = all.getOrNull(i + 1)
            if (next != null && next.first != side && overlaps(delta, next.second)) {
                // Same region changed on both sides. Identical edits are fine; anything else is a clash.
                if (delta.position == next.second.position &&
                    delta.source == next.second.source &&
                    delta.target == next.second.target
                ) {
                    i += 1 // skip the duplicate, apply this one
                } else {
                    return Result.Conflict
                }
            }
            if (start < cursor) return Result.Conflict
            out += baseLines.subList(cursor, start)
            out += delta.target
            cursor = end
            i += 1
        }
        out += baseLines.subList(cursor, baseLines.size)
        return Result.Merged(out.joinToString("\n"))
    }

    private fun overlaps(a: LineDiff.Delta, b: LineDiff.Delta): Boolean {
        // Two pure inserts at the same point clash; an insert adjacent to a change does not.
        if (a.source.isEmpty() && b.source.isEmpty()) return a.position == b.position
        return a.position < b.endPosition && b.position < a.endPosition
    }

    private enum class Side { LOCAL, SERVER }
}
