package com.keltruc.mymemos.data.config

import com.keltruc.mymemos.model.AppConfig
import com.keltruc.mymemos.model.NoteColour
import com.keltruc.mymemos.model.RecurringTemplate
import com.keltruc.mymemos.model.Reminder
import com.keltruc.mymemos.model.TagStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCodecTest {
    private val config = AppConfig(
        tagStyles = mapOf("work" to TagStyle(emoji = "💼", colour = NoteColour.BLUE), "home" to TagStyle(emoji = "🏠")),
        reminders = listOf(Reminder("r1", "memos/abc", 1_800_000_000_000L, "call")),
        recurring = listOf(RecurringTemplate("Daily journal", 21, 0)),
        weeklyDigest = true,
    )

    @Test
    fun `round trips through the memo text`() {
        val text = ConfigCodec.encode(config)
        assertTrue(ConfigCodec.isConfigMemo(text))
        assertTrue(text.contains("#mymemos/config"))
        assertEquals(config, ConfigCodec.decode(text))
    }

    @Test
    fun `garbage or a missing block gives defaults rather than a crash`() {
        assertEquals(AppConfig(), ConfigCodec.decode("#mymemos/config\n\n```json\n{not json\n```"))
        assertEquals(AppConfig(), ConfigCodec.decode("just some memo"))
    }
}
