package com.keltruc.mymemos.data.text

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
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

    fun parse(line: String, today: LocalDate = LocalDate.now()): Match? {
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
            "tomorrow", "tmr", "tmrw" -> return today.plusDays(1)
            "nextweek" -> return today.plusWeeks(1)
        }
        weekdays.entries.firstOrNull { word.startsWith(it.key) }?.let { (_, dow) ->
            var d = today
            while (d.dayOfWeek != dow) d = d.plusDays(1)
            return d
        }
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").matchEntire(word)?.let { m ->
            val (y, mo, d) = m.destructured
            return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
        }
        Regex("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?").matchEntire(word)?.let { m ->
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].let { if (it.isEmpty()) today.year else if (it.length == 2) 2000 + it.toInt() else it.toInt() }
            val date = runCatching { LocalDate.of(y, mo, d) }.getOrNull() ?: return null
            // A day/month without a year that has already passed means next year.
            return if (m.groupValues[3].isEmpty() && date.isBefore(today)) date.plusYears(1) else date
        }
        return null
    }

    fun label(date: LocalDate, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date.isBefore(today) -> "Overdue"
        date.isBefore(today.plusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        else -> "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, locale)}"
    }
}
