package shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970

/** Bridges a kotlinx date to the NSDate that NSDateFormatter wants. */
@OptIn(ExperimentalForeignApi::class)
internal fun LocalDate.toNSDate(): NSDate {
    val seconds = atStartOfDayIn(TimeZone.currentSystemDefault()).epochSeconds
    return NSDate.dateWithTimeIntervalSince1970(seconds.toDouble())
}
