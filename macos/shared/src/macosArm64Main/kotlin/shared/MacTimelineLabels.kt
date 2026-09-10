package shared

import com.keltruc.mymemos.data.timeline.TimelineGrouping
import com.keltruc.mymemos.data.timeline.TimelineGrouping.Bucket
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/**
 * The macOS half of [TimelineGrouping]: the words above each group, written by NSDateFormatter
 * so they come out in the reader's language, the same job `java.time` does on Android.
 */
@OptIn(ExperimentalForeignApi::class)
internal class MacTimelineLabels : TimelineGrouping.Labels {

    private fun formatter(template: String) = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        setLocalizedDateFormatFromTemplate(template)
    }

    private val day = formatter("EEEEdMMMM")
    private val dayWithYear = formatter("dMMMMy")
    private val week = formatter("dMMMM")
    private val month = formatter("MMMMy")

    override fun label(bucket: Bucket, today: LocalDate): String = when (bucket) {
        is Bucket.Pinned -> "Pinned"
        is Bucket.Day -> dayLabel(bucket.date, today)
        is Bucket.Week -> "Week of ${week.stringFromDate(bucket.start.toNSDate())}"
        is Bucket.Month -> month.stringFromDate(bucket.start.toNSDate())
    }

    private fun dayLabel(date: LocalDate, today: LocalDate): String =
        when (TimelineGrouping.daysAgo(date, today)) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> {
                val formatter = if (date.year == today.year) day else dayWithYear
                formatter.stringFromDate(date.toNSDate())
            }
        }

    companion object {
        /** Weeks start on a different day depending on where you are; the calendar knows. */
        fun firstDayOfWeek(): DayOfWeek {
            // NSCalendar counts Sunday as 1; DayOfWeek counts Monday as 1.
            val sundayFirst = NSCalendar.currentCalendar.firstWeekday.toInt()
            return DayOfWeek(if (sundayFirst == 1) 7 else sundayFirst - 1)
        }
    }
}
