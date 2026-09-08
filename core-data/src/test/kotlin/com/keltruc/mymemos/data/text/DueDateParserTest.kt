package com.keltruc.mymemos.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DueDateParserTest {
    private val today = LocalDate.of(2026, 9, 8) // a Tuesday

    @Test fun today() = assertEquals(today, DueDateParser.parse("- [ ] pay rent @today", today)?.date)
    @Test fun tomorrow() = assertEquals(today.plusDays(1), DueDateParser.parse("call mum @tomorrow", today)?.date)
    @Test fun `weekday is the next occurrence`() = assertEquals(LocalDate.of(2026, 9, 11), DueDateParser.parse("gym @fri", today)?.date)
    @Test fun `same weekday means today`() = assertEquals(today, DueDateParser.parse("x @tuesday", today)?.date)
    @Test fun iso() = assertEquals(LocalDate.of(2026, 12, 25), DueDateParser.parse("gifts @2026-12-25", today)?.date)
    @Test fun `day month rolls to next year when past`() = assertEquals(LocalDate.of(2027, 1, 5), DueDateParser.parse("x @5/1", today)?.date)
    @Test fun `day month this year when ahead`() = assertEquals(LocalDate.of(2026, 10, 1), DueDateParser.parse("x @1/10", today)?.date)
    @Test fun `email addresses are not dates`() = assertNull(DueDateParser.parse("mail rory@example.com", today))
    @Test fun `unknown words are ignored`() = assertNull(DueDateParser.parse("ping @someone", today))

    @Test
    fun `suggestions offer today, tomorrow and the days after`() {
        val monday = LocalDate.of(2026, 9, 7)
        val tokens = DueDateParser.suggest("", monday, Locale.UK).map { it.token }
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
            val today = LocalDate.of(2026, 9, 7).plusDays(dayOffset)
            for (s in DueDateParser.suggest("", today, Locale.UK)) {
                val parsed = DueDateParser.parse("do a thing @${s.token}", today)
                assertEquals("token @${s.token} on $today", s.date, parsed?.date)
            }
        }
    }

    @Test
    fun `a prefix narrows the suggestions`() {
        val monday = LocalDate.of(2026, 9, 7)
        assertEquals(listOf("today", "tomorrow"), DueDateParser.suggest("to", monday, Locale.UK).map { it.token })
        assertEquals(listOf("thursday"), DueDateParser.suggest("th", monday, Locale.UK).map { it.token })
        assertEquals(emptyList<String>(), DueDateParser.suggest("zz", monday, Locale.UK).map { it.token })
    }

    @Test
    fun `only the further out suggestions carry a date hint`() {
        val monday = LocalDate.of(2026, 9, 7)
        val byToken = DueDateParser.suggest("", monday, Locale.UK).associate { it.token to it.hint }
        assertEquals("", byToken["today"])
        assertEquals("", byToken["tomorrow"])
        // The exact wording is the JDK's to decide and shifts with its CLDR data, so this only
        // asserts that a weekday carries the day of the month with it.
        assertTrue(byToken.getValue("wednesday").startsWith("9 "))
    }
}
