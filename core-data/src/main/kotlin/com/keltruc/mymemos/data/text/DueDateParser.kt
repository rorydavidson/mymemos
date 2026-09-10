package com.keltruc.mymemos.data.text

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import java.util.Locale

/**
 * Finds a due date written into a task line: `@today`, `@tomorrow`, `@mon` … `@sunday`
 * (next occurrence, today counts), `@2026-09-12`, `@12/9` or `@12/9/2026` (day first).
 */
object DueDateParser {
    private val token = Regex("(?<!\\S)@([A-Za-z]+|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?)\\b")
    private val weekdays = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )

    data class Match(val date: LocalDate, val token: String)

    /**
     * A completion the editor can offer after the user types "@". [token] is what gets inserted,
     * without the "@"; [hint] is a human date for the ones whose name does not give it away.
     */
    data class Suggestion(val token: String, val date: LocalDate, val showsDate: Boolean)

    fun parse(line: String, today: LocalDate): Match? {
        for (m in token.findAll(line)) {
            val raw = m.groupValues[1]
            val date = resolve(raw.lowercase(), today) ?: continue
            return Match(date, m.value)
        }
        return null
    }

    private fun resolve(word: String, today: LocalDate): LocalDate? {
        when (word) {
            "today", "tod" -> return today
            "tomorrow", "tmr", "tmrw" -> return today.plus(1, DateTimeUnit.DAY)
            "nextweek" -> return today.plus(1, DateTimeUnit.WEEK)
        }
        weekdays.entries.firstOrNull { word.startsWith(it.key) }?.let { (_, dow) ->
            var d = today
            while (d.dayOfWeek != dow) d = d.plus(1, DateTimeUnit.DAY)
            return d
        }
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").matchEntire(word)?.let { m ->
            val (y, mo, d) = m.destructured
            return runCatching { LocalDate(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
        }
        Regex("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?").matchEntire(word)?.let { m ->
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].let { if (it.isEmpty()) today.year else if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            val date = runCatching { LocalDate(y, mo, d) }.getOrNull() ?: return null
            // A day/month without a year that has already passed means next year.
            return if (m.groupValues[3].isEmpty() && date < today) date.plus(1, DateTimeUnit.YEAR) else date
        }
        return null
    }

    /**
     * Completions for a partly typed "@" token, in date order. An empty [prefix] offers the lot.
     *
     * Tokens are deliberately the English weekday names rather than localised ones, because
     * [parse] only knows the English forms. Whether a suggestion also shows a date, and how that
     * date reads, is the caller's business: see [Labels].
     */
    fun suggest(prefix: String, today: LocalDate): List<Suggestion> {
        val all = buildList {
            add(Suggestion("today", today, showsDate = false))
            add(Suggestion("tomorrow", today.plus(1, DateTimeUnit.DAY), showsDate = false))
            // From two days out a weekday name reads better than a date. It stops at six days
            // because the seventh wraps back to today's weekday, which [resolve] would then read
            // as today rather than a week away.
            for (ahead in 2..6) {
                val date = today.plus(ahead, DateTimeUnit.DAY)
                add(Suggestion(date.dayOfWeek.name.lowercase(), date, showsDate = true))
            }
        }
        return all.filter { it.token.startsWith(prefix, ignoreCase = true) }
    }

    /** How a due date stands relative to today. Naming it is [Labels]' job. */
    enum class Relative { TODAY, TOMORROW, OVERDUE, THIS_WEEK, LATER }

    fun relative(date: LocalDate, today: LocalDate): Relative = when {
        date == today -> Relative.TODAY
        date == today.plus(1, DateTimeUnit.DAY) -> Relative.TOMORROW
        date < today -> Relative.OVERDUE
        date < today.plus(7, DateTimeUnit.DAY) -> Relative.THIS_WEEK
        else -> Relative.LATER
    }

    /** Words for a due date, in the reader's language. Implemented per platform. */
    interface Labels {
        /** The short date beside a suggestion, e.g. "12 Sep". */
        fun hint(date: LocalDate): String

        /** How a due date reads on a task, e.g. "Overdue" or "Thursday". */
        fun label(date: LocalDate, today: LocalDate): String
    }
}
