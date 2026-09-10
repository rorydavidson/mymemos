package com.keltruc.mymemos.data.notify

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleTest {

    private val london = TimeZone.of("Europe/London")

    private fun at(text: String, zone: TimeZone = london) =
        LocalDateTime.parse(text).toInstant(zone)

    private fun local(instant: kotlin.time.Instant, zone: TimeZone = london) =
        instant.toLocalDateTime(zone).toString()

    @Test
    fun `a time still to come today is today`() {
        assertEquals("2026-09-10T09:00", local(Schedule.nextDaily(9, 0, at("2026-09-10T07:30"), london)))
    }

    @Test
    fun `a time already gone is tomorrow`() {
        assertEquals("2026-09-11T09:00", local(Schedule.nextDaily(9, 0, at("2026-09-10T09:30"), london)))
    }

    @Test
    fun `the exact minute counts as gone so an alarm does not fire twice`() {
        assertEquals("2026-09-11T09:00", local(Schedule.nextDaily(9, 0, at("2026-09-10T09:00"), london)))
    }

    /**
     * British clocks go forward at 01:00 on 29 March 2026, so 01:30 does not exist that day.
     * It shifts by the size of the gap rather than being dropped, so the reminder arrives an
     * hour late that morning instead of not at all.
     */
    @Test
    fun `an hour the clocks skipped still produces a real time in the future`() {
        val now = at("2026-03-28T02:00")
        val next = Schedule.nextDaily(1, 30, now, london)
        assertTrue(next > now, "got $next, which is not after $now")
        assertEquals("2026-03-29T02:30", local(next))
    }

    @Test
    fun `the clocks going back does not skip the day`() {
        // They go back at 02:00 on 25 October 2026, so 01:30 happens twice.
        assertEquals("2026-10-25T01:30", local(Schedule.nextDaily(1, 30, at("2026-10-24T09:00"), london)))
    }

    @Test
    fun `a weekly slot later the same day is today`() {
        // 2026-09-10 is a Thursday.
        assertEquals("2026-09-10T18:00", local(Schedule.nextWeekly(DayOfWeek.THURSDAY, 18, 0, at("2026-09-10T07:00"), london)))
    }

    @Test
    fun `a weekly slot that has gone is next week`() {
        assertEquals("2026-09-17T18:00", local(Schedule.nextWeekly(DayOfWeek.THURSDAY, 18, 0, at("2026-09-10T19:00"), london)))
    }

    @Test
    fun `a weekly slot later in the week is this week`() {
        assertEquals("2026-09-13T18:00", local(Schedule.nextWeekly(DayOfWeek.SUNDAY, 18, 0, at("2026-09-10T19:00"), london)))
    }

    @Test
    fun `a template already written today is not written again`() {
        assertFalse(Schedule.shouldCreate("# Thursday 10 September 2026", listOf("# Thursday 10 September 2026", "shopping")))
    }

    @Test
    fun `a template not written today is written`() {
        assertTrue(Schedule.shouldCreate("# Thursday 10 September 2026", listOf("shopping")))
    }

    @Test
    fun `a template with nothing on its first line is never written`() {
        assertFalse(Schedule.shouldCreate("   ", emptyList()))
    }

    @Test
    fun `the first line skips the blank ones above it`() {
        assertEquals("# Title", Schedule.firstLine("\n\n# Title\nbody"))
    }
}
