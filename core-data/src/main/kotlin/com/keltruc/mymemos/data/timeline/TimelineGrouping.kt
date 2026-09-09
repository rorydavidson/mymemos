package com.keltruc.mymemos.data.timeline

import com.keltruc.mymemos.data.text.atZone
import com.keltruc.mymemos.model.Memo
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Groups the timeline so that recent memos stay day by day while older ones fold up: days in the
 * current week, whole weeks for earlier weeks of the current month, whole months before that.
 * Scrolling back through a year is then a handful of headers rather than three hundred.
 */
object TimelineGrouping {

    /**
     * One header and the memos under it. [key] is what the collapsed state is stored against: it
     * is derived from the dates rather than the label, so collapsing "Today" does not reappear as
     * a collapsed tomorrow, and a translated label does not lose the user's choice.
     */
    data class Group(val key: String, val label: String, val memos: List<Memo>)

    const val PINNED_KEY = "pinned"

    /**
     * [byModified] groups on the update time instead of the create time, so the headers agree with
     * whichever order the timeline is in.
     */
    fun group(
        memos: List<Memo>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        byModified: Boolean = false,
    ): List<Group> {
        val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
        val dayYearFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
        val weekFormat = DateTimeFormatter.ofPattern("d MMMM", locale)
        val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
        val thisWeek = startOfWeek(today, firstDayOfWeek)

        val pinned = memos.filter { it.pinned }
        val rest = memos.filterNot { it.pinned }

        // groupBy keeps the order memos arrive in, which is already newest first.
        val groups = rest.groupBy { memo ->
            val date = (if (byModified) memo.updateTime else memo.createTime).atZone(zone).toLocalDate()
            val weekStart = startOfWeek(date, firstDayOfWeek)
            when {
                weekStart == thisWeek -> Bucket.Day(date)
                date.year == today.year && date.month == today.month -> Bucket.Week(weekStart)
                else -> Bucket.Month(date.withDayOfMonth(1))
            }
        }.map { (bucket, list) ->
            when (bucket) {
                is Bucket.Day -> Group("day:${bucket.date}", dayLabel(bucket.date, today, dayFormat, dayYearFormat), list)
                is Bucket.Week -> Group("week:${bucket.start}", "Week of ${weekFormat.format(bucket.start)}", list)
                is Bucket.Month -> Group("month:${bucket.start}", monthFormat.format(bucket.start), list)
            }
        }
        return if (pinned.isEmpty()) groups else listOf(Group(PINNED_KEY, "Pinned", pinned)) + groups
    }

    private fun dayLabel(date: LocalDate, today: LocalDate, dayFormat: DateTimeFormatter, dayYearFormat: DateTimeFormatter): String =
        when (ChronoUnit.DAYS.between(date, today)) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> if (date.year == today.year) dayFormat.format(date) else dayYearFormat.format(date)
        }

    private fun startOfWeek(date: LocalDate, firstDayOfWeek: java.time.DayOfWeek): LocalDate {
        val shift = (date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        return date.minusDays(shift.toLong())
    }

    private sealed interface Bucket {
        data class Day(val date: LocalDate) : Bucket
        data class Week(val start: LocalDate) : Bucket
        data class Month(val start: LocalDate) : Bucket
    }
}
