package com.keltruc.mymemos.data.text

import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/**
 * The JVM half of [DueDateParser]: naming a due date in the reader's language. A macOS build
 * supplies its own against `NSDateFormatter`.
 */
class JavaTimeDueDateLabels(private val locale: Locale = Locale.getDefault()) : DueDateParser.Labels {

    private val short = DateTimeFormatter.ofPattern("d MMM", locale)

    override fun hint(date: LocalDate): String = short.format(date.toJavaLocalDate())

    override fun label(date: LocalDate, today: LocalDate): String =
        when (DueDateParser.relative(date, today)) {
            DueDateParser.Relative.TODAY -> "Today"
            DueDateParser.Relative.TOMORROW -> "Tomorrow"
            DueDateParser.Relative.OVERDUE -> "Overdue"
            DueDateParser.Relative.THIS_WEEK ->
                date.toJavaLocalDate().dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            DueDateParser.Relative.LATER -> {
                val java = date.toJavaLocalDate()
                "${java.dayOfMonth} ${java.month.getDisplayName(TextStyle.SHORT, locale)}"
            }
        }
}
