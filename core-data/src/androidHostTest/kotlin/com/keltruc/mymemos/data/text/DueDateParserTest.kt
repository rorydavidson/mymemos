package com.keltruc.mymemos.data.text

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.LocalDate
import java.util.Locale

class DueDateParserTest {
    private val today = LocalDate(2026, 9, 8) // a Tuesday

    @Test fun today() = assertEquals(today, DueDateParser.parse("- [ ] pay rent @today", today)?.date)
    @Test fun tomorrow() = assertEquals(today.plus(1, DateTimeUnit.DAY), DueDateParser.parse("call mum @tomorrow", today)?.date)
    @Test fun `weekday is the next occurrence`() = assertEquals(LocalDate(2026, 9, 11), DueDateParser.parse("gym @fri", today)?.date)
    @Test fun `same weekday means today`() = assertEquals(today, DueDateParser.parse("x @tuesday", today)?.date)
    @Test fun iso() = assertEquals(LocalDate(2026, 12, 25), DueDateParser.parse("gifts @2026-12-25", today)?.date)
    @Test fun `day month rolls to next year when past`() = assertEquals(LocalDate(2027, 1, 5), DueDateParser.parse("x @5/1", today)?.date)
    @Test fun `day month this year when ahead`() = assertEquals(LocalDate(2026, 10, 1), DueDateParser.parse("x @1/10", today)?.date)
    @Test fun `email addresses are not dates`() = assertNull(DueDateParser.parse("mail rory@example.com", today))
    @Test fun `unknown words are ignored`() = assertNull(DueDateParser.parse("ping @someone", today))

    @Test
    fun `suggestions offer today, tomorrow and the days after`() {
        val monday = LocalDate(2026, 9, 7)
        val tokens = DueDateParser.suggest("", monday).map { it.token }
        assertEquals(
            listOf("today", "tomorrow", "wednesday", "thursday", "friday", "saturday", "sunday"),
            tokens,
        )
    }

    @Test
    fun `every suggestion resolves to the date it advertises`() {
        // The whole point of driving the completions from the parser: a chip must never insert a
        // token that then reads as a different day.
        for (dayOffset in 0L..6L) {
            val today = LocalDate(2026, 9, 7).plus(dayOffset, DateTimeUnit.DAY)
            for (s in DueDateParser.suggest("", today)) {
                val parsed = DueDateParser.parse("do a thing @${s.token}", today)
                assertEquals("token @${s.token} on $today", s.date, parsed?.date)
            }
        }
    }

    @Test
    fun `a prefix narrows the suggestions`() {
        val monday = LocalDate(2026, 9, 7)
        assertEquals(listOf("today", "tomorrow"), DueDateParser.suggest("to", monday).map { it.token })
        assertEquals(listOf("thursday"), DueDateParser.suggest("th", monday).map { it.token })
        assertEquals(emptyList<String>(), DueDateParser.suggest("zz", monday).map { it.token })
    }

    @Test
    fun `only the further out suggestions carry a date hint`() {
        val monday = LocalDate(2026, 9, 7)
        val suggestions = DueDateParser.suggest("", monday).associateBy { it.token }
        assertEquals(false, suggestions.getValue("today").showsDate)
        assertEquals(false, suggestions.getValue("tomorrow").showsDate)
        assertEquals(true, suggestions.getValue("wednesday").showsDate)

        // The wording of the hint is the platform's to decide and shifts with its CLDR data,
        // so this only asserts that a weekday carries the day of the month with it.
        val labels = JavaTimeDueDateLabels(Locale.UK)
        assertTrue(labels.hint(suggestions.getValue("wednesday").date).startsWith("9 "))
    }
}
