package com.keltruc.mymemos.data.sync

import com.github.difflib.DiffUtils
import com.github.difflib.patch.AbstractDelta

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
        val localDeltas = DiffUtils.diff(baseLines, local.lines()).deltas.map { Side.LOCAL to it }
        val serverDeltas = DiffUtils.diff(baseLines, server.lines()).deltas.map { Side.SERVER to it }
        val all = (localDeltas + serverDeltas).sortedWith(compareBy({ it.second.source.position }, { it.first }))

        val out = mutableListOf<String>()
        var cursor = 0
        var i = 0
        while (i < all.size) {
            val (side, delta) = all[i]
            val start = delta.source.position
            val end = start + delta.source.size()
            val next = all.getOrNull(i + 1)
            if (next != null && next.first != side && overlaps(delta, next.second)) {
                // Same region changed on both sides. Identical edits are fine; anything else is a clash.
                if (delta.source.position == next.second.source.position &&
                    delta.source.lines == next.second.source.lines &&
                    delta.target.lines == next.second.target.lines
                ) {
                    i += 1 // skip the duplicate, apply this one
                } else {
                    return Result.Conflict
                }
            }
            if (start < cursor) return Result.Conflict
            out += baseLines.subList(cursor, start)
            out += delta.target.lines
            cursor = end
            i += 1
        }
        out += baseLines.subList(cursor, baseLines.size)
        return Result.Merged(out.joinToString("\n"))
    }

    private fun overlaps(a: AbstractDelta<String>, b: AbstractDelta<String>): Boolean {
        val aStart = a.source.position
        val aEnd = aStart + a.source.size()
        val bStart = b.source.position
        val bEnd = bStart + b.source.size()
        // Two pure inserts at the same point clash; an insert adjacent to a change does not.
        if (a.source.size() == 0 && b.source.size() == 0) return aStart == bStart
        return aStart < bEnd && bStart < aEnd
    }

    private enum class Side { LOCAL, SERVER }
}
