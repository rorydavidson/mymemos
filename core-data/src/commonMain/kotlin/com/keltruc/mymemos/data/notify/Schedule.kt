package com.keltruc.mymemos.data.notify

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * When a repeating thing next comes round. Date arithmetic only: what to do at that moment
 * belongs to the platform, which is why nothing here touches an alarm or a notification.
 *
 * Both clients ask these questions, and an answer that differed between them would show up as
 * a recurring memo created twice on the same day, so the arithmetic is settled once here.
 */
object Schedule {

    /**
     * The next [hour]:[minute] strictly after [now]. Today's if it has not gone yet, otherwise
     * tomorrow's.
     *
     * An hour the clocks skipped is not dropped: the local time shifts forward by the size of
     * the gap, so a 01:30 reminder arrives at 02:30 on the morning the clocks go forward. The
     * loop is what guarantees the answer is still in the future when that happens.
     */
    fun nextDaily(hour: Int, minute: Int, now: Instant, zone: TimeZone): Instant {
        var day = now.toLocalDateTime(zone).date
        repeat(DAYS_TO_TRY) {
            val at = day.atTime(LocalTime(hour, minute)).toInstant(zone)
            if (at > now) return at
            day = day.plus(1, DateTimeUnit.DAY)
        }
        error("no $hour:$minute within $DAYS_TO_TRY days of $now")
    }

    /** The next [day] at [hour]:[minute] strictly after [now]. */
    fun nextWeekly(day: DayOfWeek, hour: Int, minute: Int, now: Instant, zone: TimeZone): Instant {
        val today = now.toLocalDateTime(zone).date
        val ahead = (day.isoDayNumber - today.dayOfWeek.isoDayNumber + 7) % 7
        val candidate = today.plus(ahead, DateTimeUnit.DAY).atTime(LocalTime(hour, minute)).toInstant(zone)
        if (candidate > now) return candidate
        return today.plus(ahead + 7, DateTimeUnit.DAY).atTime(LocalTime(hour, minute)).toInstant(zone)
    }

    /**
     * Whether a recurring template should still produce a memo, given the first lines of what
     * was written today.
     *
     * The first line is the whole test. It is what the user sees in the list, and a template
     * that starts with today's date produces a line that only matches the memo it made this
     * morning, which is the case that matters: opening the Mac after the phone already ran the
     * template should not write it out again.
     */
    fun shouldCreate(templateFirstLine: String, firstLinesWrittenToday: List<String>): Boolean {
        if (templateFirstLine.isBlank()) return false
        return firstLinesWrittenToday.none { it == templateFirstLine }
    }

    /** The first line that carries anything, which is what [shouldCreate] compares. */
    fun firstLine(content: String): String =
        content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()

    private const val DAYS_TO_TRY = 3
}
