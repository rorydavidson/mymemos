package com.keltruc.mymemos.data.repository

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The JVM half of [TemplateValues]. A macOS build supplies its own. */
class JavaTimeTemplateValues(
    private val today: LocalDate = LocalDate.now(),
    private val now: LocalTime = LocalTime.now(),
    private val locale: Locale = Locale.getDefault(),
) : TemplateValues {
    override val date: String get() = today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    override val isoDate: String get() = today.toString()
    override val time: String get() = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    override val weekday: String get() = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    override val month: String get() = today.month.getDisplayName(TextStyle.FULL, locale)
    override val year: String get() = today.year.toString()
}
