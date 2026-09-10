package com.keltruc.mymemos.data.sync

/**
 * Line diff by Myers' algorithm, replacing java-diff-utils, which is JVM only.
 *
 * Only what [ThreeWayMerge] needs: the runs of lines that differ, each as a position in the
 * base, the base lines it covers and the lines that replace them. Myers is O(ND) in the size
 * of the edit rather than the size of the file, which suits the case here: two edits of the
 * same memo, usually differing by a line or two.
 */
internal object LineDiff {

    /**
     * One run of change. [position] is where it starts in the base; [source] is what was there,
     * empty for a pure insertion; [target] is what replaces it, empty for a pure deletion.
     */
    data class Delta(val position: Int, val source: List<String>, val target: List<String>) {
        val endPosition: Int get() = position + source.size
    }

    fun deltas(base: List<String>, other: List<String>): List<Delta> {
        // Identical ends carry no information and shrink the search, which is the whole point.
        var prefix = 0
        while (prefix < base.size && prefix < other.size && base[prefix] == other[prefix]) prefix++
        var suffix = 0
        while (
            suffix < base.size - prefix &&
            suffix < other.size - prefix &&
            base[base.size - 1 - suffix] == other[other.size - 1 - suffix]
        ) {
            suffix++
        }

        val a = base.subList(prefix, base.size - suffix)
        val b = other.subList(prefix, other.size - suffix)
        if (a.isEmpty() && b.isEmpty()) return emptyList()

        return coalesce(script(a, b), a, b).map { it.copy(position = it.position + prefix) }
    }

    private enum class Op { EQUAL, DELETE, INSERT }

    /** Myers' greedy algorithm, keeping each round's frontier so the path can be walked back. */
    private fun script(a: List<String>, b: List<String>): List<Op> {
        val n = a.size
        val m = b.size
        val max = n + m
        val offset = max
        val v = IntArray(2 * max + 1)
        val trace = mutableListOf<IntArray>()

        var reached = false
        for (d in 0..max) {
            trace += v.copyOf()
            var k = -d
            while (k <= d) {
                var x = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    v[offset + k + 1]
                } else {
                    v[offset + k - 1] + 1
                }
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) {
                    x++
                    y++
                }
                v[offset + k] = x
                if (x >= n && y >= m) {
                    reached = true
                    break
                }
                k += 2
            }
            if (reached) break
        }

        val ops = ArrayDeque<Op>()
        var x = n
        var y = m
        for (d in trace.indices.reversed()) {
            val frontier = trace[d]
            val k = x - y
            val previousK = if (k == -d || (k != d && frontier[offset + k - 1] < frontier[offset + k + 1])) {
                k + 1
            } else {
                k - 1
            }
            val previousX = frontier[offset + previousK]
            val previousY = previousX - previousK
            while (x > previousX && y > previousY) {
                ops.addFirst(Op.EQUAL)
                x--
                y--
            }
            if (d > 0) {
                if (x == previousX) {
                    ops.addFirst(Op.INSERT)
                    y--
                } else {
                    ops.addFirst(Op.DELETE)
                    x--
                }
            }
        }
        return ops.toList()
    }

    /** Turns the per-line edit script into one delta per run of change. */
    private fun coalesce(ops: List<Op>, a: List<String>, b: List<String>): List<Delta> {
        val deltas = mutableListOf<Delta>()
        var ai = 0
        var bi = 0
        var i = 0
        while (i < ops.size) {
            if (ops[i] == Op.EQUAL) {
                ai++
                bi++
                i++
                continue
            }
            val startA = ai
            val startB = bi
            while (i < ops.size && ops[i] != Op.EQUAL) {
                when (ops[i]) {
                    Op.DELETE -> ai++
                    Op.INSERT -> bi++
                    Op.EQUAL -> Unit
                }
                i++
            }
            deltas += Delta(startA, a.subList(startA, ai).toList(), b.subList(startB, bi).toList())
        }
        return deltas
    }
}
