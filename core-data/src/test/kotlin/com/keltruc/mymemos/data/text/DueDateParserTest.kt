package com.keltruc.mymemos.data.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

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
}
