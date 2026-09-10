package com.keltruc.mymemos.data.timeline

import com.keltruc.mymemos.data.timeline.TimelineGrouping.Bucket
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/**
 * The JVM half of [TimelineGrouping]: the words above each group, written with `java.time`
 * because that is what knows the month names in the reader's language. A macOS build supplies
 * its own against `NSDateFormatter`.
 */
class JavaTimeTimelineLabels(private val locale: Locale = Locale.getDefault()) : TimelineGrouping.Labels {

    private val day = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val dayWithYear = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val week = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    override fun label(bucket: Bucket, today: LocalDate): String = when (bucket) {
        is Bucket.Pinned -> "Pinned"
        is Bucket.Day -> dayLabel(bucket.date, today)
        is Bucket.Week -> "Week of ${week.format(bucket.start.toJavaLocalDate())}"
        is Bucket.Month -> month.format(bucket.start.toJavaLocalDate())
    }

    private fun dayLabel(date: LocalDate, today: LocalDate): String =
        when (TimelineGrouping.daysAgo(date, today)) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> {
                val formatter = if (date.year == today.year) day else dayWithYear
                formatter.format(date.toJavaLocalDate())
            }
        }

    companion object {
        /** Weeks start on a different day depending on where you are; the locale knows. */
        fun firstDayOfWeek(locale: Locale = Locale.getDefault()): kotlinx.datetime.DayOfWeek =
            kotlinx.datetime.DayOfWeek(WeekFields.of(locale).firstDayOfWeek.value)
    }
}
