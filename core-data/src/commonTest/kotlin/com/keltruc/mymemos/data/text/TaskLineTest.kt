package com.keltruc.mymemos.data.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskLineTest {

    @Test
    fun `finds unticked tasks and leaves ticked ones alone`() {
        val content = "# Jobs\n- [ ] milk\n- [x] bins\n* [ ] hoover\n1. [ ] ring the plumber\n- not a task"
        assertEquals(
            listOf(1 to "milk", 3 to "hoover", 4 to "ring the plumber"),
            TaskLine.openTasks(content).map { it.lineIndex to it.text },
        )
    }

    @Test
    fun `ticking keeps the marker and anything after the box`() {
        val content = "  - [ ] milk @tomorrow"
        assertEquals("  - [x] milk @tomorrow", TaskLine.toggle(content, 0, checked = true))
    }

    @Test
    fun `unticking works the same way round`() {
        assertEquals("1. [ ] bins", TaskLine.toggle("1. [x] bins", 0, checked = false))
    }

    @Test
    fun `a line that is not a task is left alone`() {
        assertNull(TaskLine.toggle("just a line", 0, checked = true))
        assertNull(TaskLine.toggle("- [ ] milk", 9, checked = true))
    }

    @Test
    fun `other lines are untouched`() {
        val content = "keep me\n- [ ] milk\nand me"
        assertEquals("keep me\n- [x] milk\nand me", TaskLine.toggle(content, 1, checked = true))
    }
}
