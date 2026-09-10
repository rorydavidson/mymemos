package com.keltruc.mymemos.data.timeline

import com.keltruc.mymemos.model.Memo
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Groups the timeline so that recent memos stay day by day while older ones fold up: days in the
 * current week, whole weeks for earlier weeks of the current month, whole months before that.
 * Scrolling back through a year is then a handful of headers rather than three hundred.
 *
 * Deciding which bucket a memo falls into is date arithmetic and lives here. Turning a bucket
 * into the words above a group is not: "Monday 31 August" has to be written in the reader's
 * language, and only the platform knows how. That is [Labels], and it is why this file has no
 * formatter in it.
 */
object TimelineGrouping {

    /** What a group covers. The label is derived from this, but separately. */
    sealed interface Bucket {
        data object Pinned : Bucket
        data class Day(val date: LocalDate) : Bucket
        data class Week(val start: LocalDate) : Bucket
        data class Month(val start: LocalDate) : Bucket
    }

    /**
     * One header and the memos under it. [key] is what the collapsed state is stored against: it
     * is derived from the dates rather than the label, so collapsing "Today" does not reappear as
     * a collapsed tomorrow, and a translated label does not lose the user's choice.
     */
    data class Group(val key: String, val bucket: Bucket, val memos: List<Memo>)

    /** Supplies the words for a bucket. Implemented per platform, against its own date formatter. */
    fun interface Labels {
        fun label(bucket: Bucket, today: LocalDate): String
    }

    const val PINNED_KEY = "pinned"

    /**
     * [byModified] groups on the update time instead of the create time, so the headers agree with
     * whichever order the timeline is in.
     *
     * [firstDayOfWeek] has no sensible default: weeks start on Monday in most of Europe and on
     * Sunday in much of the world, and guessing would put a memo under the wrong header for half
     * the planet. The caller reads it from the locale and passes it in.
     */
    fun group(
        memos: List<Memo>,
        today: LocalDate,
        zone: TimeZone,
        firstDayOfWeek: DayOfWeek,
        byModified: Boolean = false,
    ): List<Group> {
        val thisWeek = startOfWeek(today, firstDayOfWeek)

        val pinned = memos.filter { it.pinned }
        val rest = memos.filterNot { it.pinned }

        // groupBy keeps the order memos arrive in, which is already newest first.
        val groups = rest.groupBy { memo ->
            val date = memo.timelineTime(byModified).toLocalDateTime(zone).date
            val weekStart = startOfWeek(date, firstDayOfWeek)
            when {
                weekStart == thisWeek -> Bucket.Day(date)
                date.year == today.year && date.month == today.month -> Bucket.Week(weekStart)
                else -> Bucket.Month(LocalDate(date.year, date.month, 1))
            }
        }.map { (bucket, list) -> Group(keyOf(bucket), bucket, list) }

        return if (pinned.isEmpty()) groups else listOf(Group(PINNED_KEY, Bucket.Pinned, pinned)) + groups
    }

    private fun keyOf(bucket: Bucket): String = when (bucket) {
        is Bucket.Pinned -> PINNED_KEY
        is Bucket.Day -> "day:${bucket.date}"
        is Bucket.Week -> "week:${bucket.start}"
        is Bucket.Month -> "month:${bucket.start}"
    }

    /** How many days back a day bucket is, which is what decides "Today" and "Yesterday". */
    fun daysAgo(date: LocalDate, today: LocalDate): Int = date.daysUntil(today)

    private fun startOfWeek(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate {
        val shift = (date.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
        return date.minus(shift, DateTimeUnit.DAY)
    }
}
