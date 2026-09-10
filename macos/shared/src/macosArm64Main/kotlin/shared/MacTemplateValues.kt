package shared

import com.keltruc.mymemos.data.repository.TemplateValues
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/**
 * The macOS half of [TemplateValues]. Written by NSDateFormatter so that {{weekday}} and
 * {{month}} come out in the reader's language, which is the whole reason the words are handed
 * to the shared code rather than formatted inside it.
 */
@OptIn(ExperimentalForeignApi::class)
internal class MacTemplateValues(private val now: NSDate = NSDate()) : TemplateValues {

    private fun formatter(build: NSDateFormatter.() -> Unit) = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        build()
    }

    override val date: String
        get() = formatter { setLocalizedDateFormatFromTemplate("dMMMMy") }.stringFromDate(now)

    // Deliberately not localised: {{isodate}} is for filing and searching, and 2026-09-10
    // sorts and matches whatever the reader's own date format happens to be.
    override val isoDate: String
        get() = formatter { dateFormat = "yyyy-MM-dd" }.stringFromDate(now)

    override val time: String
        get() = formatter { dateFormat = "HH:mm" }.stringFromDate(now)

    override val weekday: String
        get() = formatter { dateFormat = "EEEE" }.stringFromDate(now)

    override val month: String
        get() = formatter { dateFormat = "MMMM" }.stringFromDate(now)

    override val year: String
        get() = formatter { dateFormat = "yyyy" }.stringFromDate(now)
}
