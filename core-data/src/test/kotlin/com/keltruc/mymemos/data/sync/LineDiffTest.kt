package com.keltruc.mymemos.data.sync

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge is the one place in this app where being wrong loses someone's writing silently
 * rather than loudly, and the diff underneath it is now ours rather than a library's. So as
 * well as the worked examples, this checks the property that has to hold for any input: apply
 * the deltas to the base and you get the other side back, exactly.
 */
class LineDiffTest {

    private fun applyTo(base: List<String>, deltas: List<LineDiff.Delta>): List<String> {
        val out = mutableListOf<String>()
        var cursor = 0
        for (delta in deltas) {
            out += base.subList(cursor, delta.position)
            out += delta.target
            cursor = delta.endPosition
        }
        out += base.subList(cursor, base.size)
        return out
    }

    private fun roundTrips(base: List<String>, other: List<String>) {
        val deltas = LineDiff.deltas(base, other)
        assertEquals(
            "deltas did not rebuild the target\n  base=$base\n  other=$other\n  deltas=$deltas",
            other,
            applyTo(base, deltas),
        )
        // Deltas must be ordered and disjoint, which is what the merge relies on to walk them.
        deltas.zipWithNext { a, b ->
            assertTrue("deltas overlap or are out of order: $a then $b", a.endPosition <= b.position)
        }
    }

    @Test
    fun `identical input produces no deltas`() {
        assertEquals(emptyList<LineDiff.Delta>(), LineDiff.deltas(listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun `a pure insertion has no source lines`() {
        val deltas = LineDiff.deltas(listOf("a", "c"), listOf("a", "b", "c"))
        assertEquals(1, deltas.size)
        assertEquals(emptyList<String>(), deltas.single().source)
        assertEquals(listOf("b"), deltas.single().target)
        assertEquals(1, deltas.single().position)
    }

    @Test
    fun `a pure deletion has no target lines`() {
        val deltas = LineDiff.deltas(listOf("a", "b", "c"), listOf("a", "c"))
        assertEquals(listOf("b"), deltas.single().source)
        assertEquals(emptyList<String>(), deltas.single().target)
    }

    @Test
    fun `separate edits stay separate deltas`() {
        val deltas = LineDiff.deltas(
            listOf("a", "b", "c", "d", "e"),
            listOf("a", "B", "c", "d", "E"),
        )
        assertEquals(2, deltas.size)
        assertEquals(1, deltas[0].position)
        assertEquals(4, deltas[1].position)
    }

    @Test
    fun `empty sides are handled`() {
        roundTrips(emptyList(), listOf("a", "b"))
        roundTrips(listOf("a", "b"), emptyList())
        roundTrips(emptyList(), emptyList())
    }

    @Test
    fun `deltas rebuild the target for randomly edited text`() {
        val random = Random(20260909)
        repeat(2000) {
            val base = List(random.nextInt(0, 30)) { random.nextInt(0, 8).toString() }
            val other = mutate(base, random)
            roundTrips(base, other)
        }
    }

    @Test
    fun `deltas rebuild the target for text made only of repeats`() {
        // Repeated lines are where a diff is most likely to line up the wrong pair.
        val random = Random(7)
        repeat(500) {
            val base = List(random.nextInt(0, 20)) { if (random.nextBoolean()) "x" else "y" }
            val other = List(random.nextInt(0, 20)) { if (random.nextBoolean()) "x" else "y" }
            roundTrips(base, other)
        }
    }

    private fun mutate(base: List<String>, random: Random): List<String> {
        val out = base.toMutableList()
        repeat(random.nextInt(0, 6)) {
            when (random.nextInt(3)) {
                0 -> out.add(random.nextInt(0, out.size + 1), random.nextInt(0, 8).toString())
                1 -> if (out.isNotEmpty()) out.removeAt(random.nextInt(out.size))
                else -> if (out.isNotEmpty()) out[random.nextInt(out.size)] = random.nextInt(0, 8).toString()
            }
        }
        return out
    }
}
