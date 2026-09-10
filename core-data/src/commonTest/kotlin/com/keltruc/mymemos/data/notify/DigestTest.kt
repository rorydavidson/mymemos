package com.keltruc.mymemos.data.notify

import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DigestTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 9, 13) // a Sunday

    private fun memo(
        created: LocalDate,
        updated: LocalDate = created,
        content: String = "note $created",
    ) = Memo(
        localId = "$created/$updated/$content", accountId = 1, remoteName = null, creator = null,
        content = content, visibility = Visibility.PRIVATE, state = MemoState.NORMAL,
        pinned = false, tags = emptyList(),
        createTime = created.atTime(LocalTime(12, 0)).toInstant(zone),
        updateTime = updated.atTime(LocalTime(12, 0)).toInstant(zone),
        snippet = "", hasTaskList = false, hasIncompleteTasks = false, hasLink = false,
        hasCode = false, location = null, attachments = emptyList(), syncStatus = SyncStatus.SYNCED,
    )

    private fun daysAgo(n: Int) = today.minus(n, DateTimeUnit.DAY)

    private val labels = object : Digest.Labels {
        override fun written(count: Int) = "$count memos written"
        override fun tasksClosed(count: Int) = "$count tasks closed"
        override fun streak(days: Int) = "a $days day streak"
        override fun worthALookAgain() = "Worth a look again:"
        override fun nothingThisWeek() = "Nothing written this week."
    }

    @Test
    fun `counts what was written in the last seven days`() {
        val all = listOf(memo(daysAgo(1)), memo(daysAgo(6)), memo(daysAgo(8)))
        assertEquals(2, Digest.summarise(all, emptySet(), today, zone).written)
    }

    @Test
    fun `a task ticked on an old memo still counts for this week`() {
        val old = memo(created = daysAgo(90), updated = daysAgo(2), content = "- [x] done\n- [ ] not")
        assertEquals(1, Digest.summarise(listOf(old), emptySet(), today, zone).tasksClosed)
    }

    @Test
    fun `a memo untouched this week contributes no tasks`() {
        val stale = memo(created = daysAgo(90), updated = daysAgo(30), content = "- [x] done")
        assertEquals(0, Digest.summarise(listOf(stale), emptySet(), today, zone).tasksClosed)
    }

    @Test
    fun `only memos older than a month come back for another look`() {
        val all = listOf(memo(daysAgo(40), content = "# Old one"), memo(daysAgo(3), content = "# Recent"))
        assertEquals(listOf("Old one"), Digest.summarise(all, emptySet(), today, zone, Random(1)).revisit)
    }

    @Test
    fun `a locked memo is never offered because its body is ciphertext`() {
        val locked = memo(daysAgo(40), content = Memo.LOCKED_PREFIX + "abcdef")
        assertEquals(emptyList(), Digest.summarise(listOf(locked), emptySet(), today, zone, Random(1)).revisit)
    }

    @Test
    fun `the streak counts back from today`() {
        val days = setOf(today, daysAgo(1), daysAgo(2), daysAgo(4))
        assertEquals(3, Digest.streak(days, today))
    }

    @Test
    fun `nothing written today yet does not break the streak`() {
        val days = setOf(daysAgo(1), daysAgo(2))
        assertEquals(2, Digest.streak(days, today))
    }

    @Test
    fun `a gap yesterday and today means no streak`() {
        assertEquals(0, Digest.streak(setOf(daysAgo(3)), today))
    }

    @Test
    fun `a streak of one is not worth saying`() {
        val text = Digest.text(Digest.Summary(1, 0, 1, emptyList()), labels)
        assertEquals("1 memos written, 0 tasks closed.", text)
    }

    @Test
    fun `the sentence puts the streak and the revisits together`() {
        val text = Digest.text(Digest.Summary(4, 7, 12, listOf("Old one", "Older still")), labels)
        assertEquals(
            "4 memos written, 7 tasks closed, a 12 day streak.\n\nWorth a look again:\n• Old one\n• Older still",
            text,
        )
    }

    @Test
    fun `an empty week says so rather than reading like a telling off`() {
        assertEquals("Nothing written this week.", Digest.text(Digest.Summary(0, 0, 0, emptyList()), labels))
    }

    @Test
    fun `a week with nothing written but tasks closed is not empty`() {
        assertTrue(Digest.text(Digest.Summary(0, 3, 0, emptyList()), labels).startsWith("0 memos written, 3 tasks closed"))
    }
}
