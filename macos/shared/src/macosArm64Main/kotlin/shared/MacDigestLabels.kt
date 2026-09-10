package shared

import com.keltruc.mymemos.data.notify.Digest

/**
 * The macOS half of [Digest.Labels].
 *
 * The counts are pluralised by hand rather than by NSLocalizedString: this app ships in
 * English only, and a stringsdict for two sentences would be ceremony without a translation
 * behind it. Should that change, this is the one file that has to.
 */
internal class MacDigestLabels : Digest.Labels {
    override fun written(count: Int): String = "$count " + if (count == 1) "memo written" else "memos written"
    override fun tasksClosed(count: Int): String = "$count " + if (count == 1) "task closed" else "tasks closed"
    override fun streak(days: Int): String = "a $days-day streak"
    override fun worthALookAgain(): String = "Worth a look again:"
    override fun nothingThisWeek(): String = "Nothing written this week. Next week is a fresh page."
}
