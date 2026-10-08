package com.keltruc.mymemos.web

import com.keltruc.mymemos.data.notify.Digest
import com.keltruc.mymemos.data.repository.TemplateValues
import com.keltruc.mymemos.data.text.DueDateParser
import com.keltruc.mymemos.data.timeline.TimelineGrouping
import com.keltruc.mymemos.data.timeline.TimelineGrouping.Bucket
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.js.Date

/**
 * The web halves of the shared Labels interfaces: the words, written by the browser's Intl in
 * the reader's language, as NSDateFormatter does on Apple platforms and java.time on Android.
 * The English phrases match the apps'.
 */
internal object WebLabels {

    private fun format(date: Date, options: dynamic): String =
        js("new Intl.DateTimeFormat(undefined, options)").format(date) as String

    private fun opts(vararg pairs: Pair<String, String>): dynamic {
        val o: dynamic = js("({})")
        for ((k, v) in pairs) o[k] = v
        return o
    }

    fun LocalDate.toJsDate(): Date = Date(year, month.ordinal, day)

    /** Weeks start on a different day depending on where you are; Intl knows, where it can say. */
    fun firstDayOfWeek(): DayOfWeek {
        val day = js(
            "(function(){ try { var l = new Intl.Locale(navigator.language); var w = l.getWeekInfo ? l.getWeekInfo() : l.weekInfo; return w && w.firstDay; } catch (e) { return undefined; } })()",
        )
        val n = (day as? Int) ?: 1
        return DayOfWeek(n.coerceIn(1, 7))
    }

    val timeline = object : TimelineGrouping.Labels {
        override fun label(bucket: Bucket, today: LocalDate): String = when (bucket) {
            is Bucket.Pinned -> "Pinned"
            is Bucket.Day -> dayLabel(bucket.date, today)
            is Bucket.Week -> "Week of " + format(bucket.start.toJsDate(), opts("day" to "numeric", "month" to "long"))
            is Bucket.Month -> format(bucket.start.toJsDate(), opts("month" to "long", "year" to "numeric"))
        }
    }

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (TimelineGrouping.daysAgo(date, today)) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> if (date.year == today.year) {
            format(date.toJsDate(), opts("weekday" to "long", "day" to "numeric", "month" to "long"))
        } else {
            format(date.toJsDate(), opts("day" to "numeric", "month" to "long", "year" to "numeric"))
        }
    }

    val dueDates = object : DueDateParser.Labels {
        override fun hint(date: LocalDate): String = format(date.toJsDate(), opts("day" to "numeric", "month" to "short"))

        override fun label(date: LocalDate, today: LocalDate): String = when (DueDateParser.relative(date, today)) {
            DueDateParser.Relative.TODAY -> "Today"
            DueDateParser.Relative.TOMORROW -> "Tomorrow"
            DueDateParser.Relative.OVERDUE -> "Overdue"
            DueDateParser.Relative.THIS_WEEK -> format(date.toJsDate(), opts("weekday" to "long"))
            DueDateParser.Relative.LATER -> hint(date)
        }
    }

    val digest = object : Digest.Labels {
        override fun written(count: Int): String = "$count " + if (count == 1) "memo written" else "memos written"
        override fun tasksClosed(count: Int): String = "$count " + if (count == 1) "task closed" else "tasks closed"
        override fun streak(days: Int): String = "a $days-day streak"
        override fun worthALookAgain(): String = "Worth a look again:"
        override fun nothingThisWeek(): String = "Nothing written this week. Next week is a fresh page."
    }

    fun templateValues(now: Date = Date()): TemplateValues = object : TemplateValues {
        override val date: String = format(now, opts("day" to "numeric", "month" to "long", "year" to "numeric"))
        // Deliberately not localised: {{isodate}} is for filing and searching.
        override val isoDate: String = "${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}"
        override val time: String = "${pad(now.getHours())}:${pad(now.getMinutes())}"
        override val weekday: String = format(now, opts("weekday" to "long"))
        override val month: String = format(now, opts("month" to "long"))
        override val year: String = now.getFullYear().toString()
    }

    private fun pad(n: Int) = n.toString().padStart(2, '0')
}
