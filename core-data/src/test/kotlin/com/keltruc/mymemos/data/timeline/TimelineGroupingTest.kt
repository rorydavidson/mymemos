package com.keltruc.mymemos.data.timeline

import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class TimelineGroupingTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 8) // a Tuesday; its week began Monday 7 September

    private fun memo(date: LocalDate, pinned: Boolean = false) = Memo(
        localId = date.toString() + pinned, accountId = 1, remoteName = null, creator = null,
        content = date.toString(), visibility = Visibility.PRIVATE, state = MemoState.NORMAL,
        pinned = pinned, tags = emptyList(), createTime = date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC),
        updateTime = date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC), snippet = "", hasTaskList = false,
        hasIncompleteTasks = false, hasLink = false, hasCode = false, location = null, attachments = emptyList(),
        syncStatus = SyncStatus.SYNCED,
    )

    private fun labels(vararg dates: LocalDate) =
        TimelineGrouping.group(dates.map { memo(it) }, today, zone, Locale.UK).map { it.label }

    @Test
    fun `this week stays day by day`() {
        assertEquals(
            listOf("Today", "Yesterday"),
            labels(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 7)),
        )
    }

    @Test
    fun `earlier weeks of this month fold into a week`() {
        // 6 September is the Sunday before, so its week began on 31 August.
        assertEquals(
            listOf("Week of 31 August"),
            labels(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 1)),
        )
    }

    @Test
    fun `earlier months fold into a month`() {
        assertEquals(
            listOf("August 2026", "July 2026"),
            labels(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 7, 3)),
        )
    }

    @Test
    fun `a day in this week counts as this week even in the previous month`() {
        // Seen from Wednesday 2 September, this week began on Monday 31 August, so a memo from
        // that Monday is still a day of its own rather than folded into August.
        val wednesday = LocalDate.of(2026, 9, 2)
        val groups = TimelineGrouping.group(listOf(memo(LocalDate.of(2026, 8, 31))), wednesday, zone, Locale.UK)
        assertEquals(listOf("Monday 31 August"), groups.map { it.label })
        assertEquals(listOf("day:2026-08-31"), groups.map { it.key })
    }

    @Test
    fun `all three tiers appear together, newest first`() {
        assertEquals(
            listOf("Today", "Week of 31 August", "August 2026"),
            labels(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 8, 20)),
        )
    }

    @Test
    fun `pinned memos lead, whatever their date`() {
        val groups = TimelineGrouping.group(
            listOf(memo(LocalDate.of(2026, 7, 1), pinned = true), memo(LocalDate.of(2026, 9, 8))),
            today, zone, Locale.UK,
        )
        assertEquals(listOf("Pinned", "Today"), groups.map { it.label })
        assertEquals(TimelineGrouping.PINNED_KEY, groups.first().key)
    }

    @Test
    fun `keys come from the date, not the label`() {
        val groups = TimelineGrouping.group(
            listOf(memo(LocalDate.of(2026, 9, 8)), memo(LocalDate.of(2026, 9, 2)), memo(LocalDate.of(2026, 8, 20))),
            today, zone, Locale.UK,
        )
        assertEquals(listOf("day:2026-09-08", "week:2026-08-31", "month:2026-08-01"), groups.map { it.key })
    }

    @Test
    fun `a memo keeps its group as the weeks roll on`() {
        // The same memo, seen from a later day: what was "Today" becomes part of a week, then a month.
        val memo = listOf(memo(LocalDate.of(2026, 9, 8)))
        fun keyOn(day: LocalDate) = TimelineGrouping.group(memo, day, zone, Locale.UK).single().key
        assertEquals("day:2026-09-08", keyOn(LocalDate.of(2026, 9, 8)))
        assertEquals("week:2026-09-07", keyOn(LocalDate.of(2026, 9, 15)))
        assertEquals("month:2026-09-01", keyOn(LocalDate.of(2026, 10, 6)))
    }
}
