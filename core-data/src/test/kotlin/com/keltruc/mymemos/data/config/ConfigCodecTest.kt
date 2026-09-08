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

    @Test
    fun `out-of-range or oversized values from the server are dropped`() {
        val body = buildString {
            append("#mymemos/config\n```json\n{\"recurring\":[{\"template\":\"x\",\"hour\":99,\"minute\":0},{\"template\":\"ok\",\"hour\":7,\"minute\":30}],")
            append("\"reminders\":[")
            append((1..ConfigCodec.MAX_REMINDERS + 50).joinToString(",") { "{\"id\":\"r$it\",\"memo\":\"memos/m\",\"at\":1}" })
            append(",{\"id\":\"\",\"memo\":\"memos/m\",\"at\":1}]}\n```")
        }
        val decoded = ConfigCodec.decode(body)
        assertEquals(listOf(RecurringTemplate("ok", 7, 30)), decoded.recurring)
        assertEquals(ConfigCodec.MAX_REMINDERS, decoded.reminders.size)
        assertTrue(decoded.reminders.none { it.id.isBlank() })
    }
}
