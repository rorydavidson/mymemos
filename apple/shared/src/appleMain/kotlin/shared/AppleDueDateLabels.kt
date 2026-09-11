package shared

import com.keltruc.mymemos.data.text.DueDateParser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/** The macOS half of [DueDateParser]: naming a due date in the reader's language. */
@OptIn(ExperimentalForeignApi::class)
internal class AppleDueDateLabels : DueDateParser.Labels {

    private fun formatter(template: String) = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        setLocalizedDateFormatFromTemplate(template)
    }

    private val short = formatter("dMMM")
    private val weekday = formatter("EEEE")

    override fun hint(date: LocalDate): String = short.stringFromDate(date.toNSDate())

    override fun label(date: LocalDate, today: LocalDate): String =
        when (DueDateParser.relative(date, today)) {
            DueDateParser.Relative.TODAY -> "Today"
            DueDateParser.Relative.TOMORROW -> "Tomorrow"
            DueDateParser.Relative.OVERDUE -> "Overdue"
            DueDateParser.Relative.THIS_WEEK -> weekday.stringFromDate(date.toNSDate())
            DueDateParser.Relative.LATER -> short.stringFromDate(date.toNSDate())
        }
}
