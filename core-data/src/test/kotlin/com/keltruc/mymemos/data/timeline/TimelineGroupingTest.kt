package com.keltruc.mymemos.data.timeline

import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate

class TimelineGroupingTest {
    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 9, 8) // a Tuesday; its week began Monday 7 September
    private val labeller = JavaTimeTimelineLabels(Locale.UK)
    private val mondayFirst = JavaTimeTimelineLabels.firstDayOfWeek(Locale.UK)

    private fun memo(date: LocalDate, pinned: Boolean = false) = Memo(
        localId = date.toString() + pinned, accountId = 1, remoteName = null, creator = null,
        content = date.toString(), visibility = Visibility.PRIVATE, state = MemoState.NORMAL,
        pinned = pinned, tags = emptyList(), createTime = date.toJavaLocalDate().atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC).toKotlinInstant(),
        updateTime = date.toJavaLocalDate().atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC).toKotlinInstant(), snippet = "", hasTaskList = false,
        hasIncompleteTasks = false, hasLink = false, hasCode = false, location = null, attachments = emptyList(),
        syncStatus = SyncStatus.SYNCED,
    )

    /** Groups the dates and labels the result, which is what the timeline itself does. */
    private fun labels(vararg dates: LocalDate) = group(dates.map { memo(it) }).map(::labelOf)

    private fun group(memos: List<Memo>, byModified: Boolean = false) =
        TimelineGrouping.group(memos, today, zone, mondayFirst, byModified)

    private fun labelOf(group: TimelineGrouping.Group, asOf: LocalDate = today) = labeller.label(group.bucket, asOf)

    @Test
    fun `this week stays day by day`() {
        assertEquals(
            listOf("Today", "Yesterday"),
            labels(LocalDate(2026, 9, 8), LocalDate(2026, 9, 7)),
        )
    }

    @Test
    fun `earlier weeks of this month fold into a week`() {
        // 6 September is the Sunday before, so its week began on 31 August.
        assertEquals(
            listOf("Week of 31 August"),
            labels(LocalDate(2026, 9, 6), LocalDate(2026, 9, 1)),
        )
    }

    @Test
    fun `earlier months fold into a month`() {
        assertEquals(
            listOf("August 2026", "July 2026"),
            labels(LocalDate(2026, 8, 25), LocalDate(2026, 7, 3)),
        )
    }

    @Test
    fun `a day in this week counts as this week even in the previous month`() {
        // Seen from Wednesday 2 September, this week began on Monday 31 August, so a memo from
        // that Monday is still a day of its own rather than folded into August.
        val wednesday = LocalDate(2026, 9, 2)
        val groups = TimelineGrouping.group(listOf(memo(LocalDate(2026, 8, 31))), wednesday, zone, mondayFirst)
        assertEquals(listOf("Monday 31 August"), groups.map { labelOf(it, wednesday) })
        assertEquals(listOf("day:2026-08-31"), groups.map { it.key })
    }

    @Test
    fun `all three tiers appear together, newest first`() {
        assertEquals(
            listOf("Today", "Week of 31 August", "August 2026"),
            labels(LocalDate(2026, 9, 8), LocalDate(2026, 9, 2), LocalDate(2026, 8, 20)),
        )
    }

    @Test
    fun `grouping by modified uses the update time`() {
        // Written in July, edited today: created-order puts it in a July group, modified-order
        // puts it under Today.
        val edited = memo(LocalDate(2026, 7, 1)).copy(
            updateTime = today.toJavaLocalDate().atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC).toKotlinInstant(),
        )
        assertEquals(
            listOf("July 2026"),
            group(listOf(edited), byModified = false).map(::labelOf),
        )
        assertEquals(
            listOf("Today"),
            group(listOf(edited), byModified = true).map(::labelOf),
        )
    }

    @Test
    fun `pinned memos lead, whatever their date`() {
        val groups = TimelineGrouping.group(
            listOf(memo(LocalDate(2026, 7, 1), pinned = true), memo(LocalDate(2026, 9, 8))),
            today, zone, mondayFirst,
        )
        assertEquals(listOf("Pinned", "Today"), groups.map(::labelOf))
        assertEquals(TimelineGrouping.PINNED_KEY, groups.first().key)
    }

    @Test
    fun `keys come from the date, not the label`() {
        val groups = TimelineGrouping.group(
            listOf(memo(LocalDate(2026, 9, 8)), memo(LocalDate(2026, 9, 2)), memo(LocalDate(2026, 8, 20))),
            today, zone, mondayFirst,
        )
        assertEquals(listOf("day:2026-09-08", "week:2026-08-31", "month:2026-08-01"), groups.map { it.key })
    }

    @Test
    fun `a memo keeps its group as the weeks roll on`() {
        // The same memo, seen from a later day: what was "Today" becomes part of a week, then a month.
        val memo = listOf(memo(LocalDate(2026, 9, 8)))
        fun keyOn(day: LocalDate) = TimelineGrouping.group(memo, day, zone, mondayFirst).single().key
        assertEquals("day:2026-09-08", keyOn(LocalDate(2026, 9, 8)))
        assertEquals("week:2026-09-07", keyOn(LocalDate(2026, 9, 15)))
        assertEquals("month:2026-09-01", keyOn(LocalDate(2026, 10, 6)))
    }
}

/** These fixtures date memos with java.time; the model carries kotlin.time. */
private fun java.time.Instant.toKotlinInstant(): Instant = Instant.fromEpochMilliseconds(toEpochMilli())
