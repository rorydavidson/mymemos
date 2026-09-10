package com.keltruc.mymemos.data.sync

import com.keltruc.mymemos.data.sync.ThreeWayMerge.Result
import kotlin.test.assertEquals
import kotlin.test.Test

class ThreeWayMergeTest {
    private val base = "line one\nline two\nline three\nline four"

    @Test
    fun `only local changed returns local`() {
        val local = "line one\nedited two\nline three\nline four"
        assertEquals(Result.Merged(local), ThreeWayMerge.merge(base, local, base))
    }

    @Test
    fun `only server changed returns server`() {
        val server = "line one\nline two\nline three\nline four\nline five"
        assertEquals(Result.Merged(server), ThreeWayMerge.merge(base, base, server))
    }

    @Test
    fun `edits to different lines combine`() {
        val local = "LOCAL one\nline two\nline three\nline four"
        val server = "line one\nline two\nline three\nSERVER four"
        assertEquals(
            Result.Merged("LOCAL one\nline two\nline three\nSERVER four"),
            ThreeWayMerge.merge(base, local, server),
        )
    }

    @Test
    fun `local append and server prepend combine`() {
        val local = "$base\nappended"
        val server = "prepended\n$base"
        assertEquals(Result.Merged("prepended\n$base\nappended"), ThreeWayMerge.merge(base, local, server))
    }

    @Test
    fun `same line edited differently is a conflict`() {
        val local = "line one\nlocal two\nline three\nline four"
        val server = "line one\nserver two\nline three\nline four"
        assertEquals(Result.Conflict, ThreeWayMerge.merge(base, local, server))
    }

    @Test
    fun `identical edits on both sides are not a conflict`() {
        val both = "line one\nsame edit\nline three\nline four"
        assertEquals(Result.Merged(both), ThreeWayMerge.merge(base, both, both))
    }

    @Test
    fun `checkbox ticked locally while server adds a line elsewhere`() {
        val b = "- [ ] milk\n- [ ] eggs"
        val local = "- [x] milk\n- [ ] eggs"
        val server = "- [ ] milk\n- [ ] eggs\n- [ ] bread"
        assertEquals(Result.Merged("- [x] milk\n- [ ] eggs\n- [ ] bread"), ThreeWayMerge.merge(b, local, server))
    }

    @Test
    fun `local delete of a line the server edited is a conflict`() {
        val local = "line one\nline three\nline four"
        val server = "line one\nline two changed\nline three\nline four"
        assertEquals(Result.Conflict, ThreeWayMerge.merge(base, local, server))
    }
}
