package com.keltruc.mymemos.data.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskListSorterTest {
    @Test
    fun `ticked tasks move below unticked, order kept`() {
        val input = "- [x] a\n- [ ] b\n- [x] c\n- [ ] d"
        assertEquals("- [ ] b\n- [ ] d\n- [x] a\n- [x] c", TaskListSorter.sortCompletedToBottom(input))
    }

    @Test
    fun `separate lists sort independently`() {
        val input = "Shopping\n- [x] milk\n- [ ] eggs\n\nChores\n- [x] bins\n- [ ] hoover"
        assertEquals(
            "Shopping\n- [ ] eggs\n- [x] milk\n\nChores\n- [ ] hoover\n- [x] bins",
            TaskListSorter.sortCompletedToBottom(input),
        )
    }

    @Test
    fun `nested indent levels are their own runs`() {
        val input = "- [x] parent\n  - [x] child a\n  - [ ] child b\n- [ ] other"
        assertEquals(
            "- [x] parent\n  - [ ] child b\n  - [x] child a\n- [ ] other",
            TaskListSorter.sortCompletedToBottom(input),
        )
    }

    @Test
    fun `content without tasks is untouched`() {
        val input = "# Title\n\nSome prose\n- bullet"
        assertEquals(input, TaskListSorter.sortCompletedToBottom(input))
    }
}
