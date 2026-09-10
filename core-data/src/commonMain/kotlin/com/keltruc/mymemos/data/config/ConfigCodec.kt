package com.keltruc.mymemos.data.config

import com.keltruc.mymemos.model.AppConfig
import com.keltruc.mymemos.model.NoteColour

import com.keltruc.mymemos.model.RecurringTemplate
import com.keltruc.mymemos.model.Reminder
import com.keltruc.mymemos.model.TagStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The config memo's text: a short explanation, the marker tag, and a JSON block. Anything
 * outside the block is ignored on read and rewritten on write.
 */
object ConfigCodec {
    const val TAG = "mymemos/config"
    const val MAX_REMINDERS = 200
    const val MAX_RECURRING = 50
    const val MAX_NOTE_CHARS = 500
    private val fence = Regex("```json\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    @Serializable
    private data class Doc(
        val version: Int = 1,
        val tagStyles: Map<String, StyleDto> = emptyMap(),
        val reminders: List<ReminderDto> = emptyList(),
        val recurring: List<RecurringDto> = emptyList(),
        val weeklyDigest: Boolean = false,
    )

    @Serializable private data class StyleDto(val emoji: String? = null, val colour: String? = null)
    @Serializable private data class ReminderDto(val id: String, val memo: String, val at: Long, val note: String = "")
    @Serializable private data class RecurringDto(val template: String, val hour: Int, val minute: Int, val enabled: Boolean = true)

    fun isConfigMemo(content: String): Boolean = content.contains("#$TAG")

    fun decode(content: String): AppConfig {
        val body = fence.find(content)?.groupValues?.get(1) ?: return AppConfig()
        val doc = runCatching { json.decodeFromString<Doc>(body) }.getOrNull() ?: return AppConfig()
        return AppConfig(
            tagStyles = doc.tagStyles.mapValues { (_, s) ->
                TagStyle(s.emoji, s.colour?.let { c -> NoteColour.entries.firstOrNull { it.name.equals(c, true) } })
            },
            // The memo syncs from the server, so treat its values as untrusted: bad hours would
            // crash the alarm scheduler on boot, and AlarmManager refuses more than 500 alarms.
            reminders = doc.reminders.take(MAX_REMINDERS)
                .filter { it.id.isNotBlank() && it.memo.isNotBlank() && it.at > 0 }
                .map { Reminder(it.id, it.memo, it.at, it.note.take(MAX_NOTE_CHARS)) },
            recurring = doc.recurring.take(MAX_RECURRING)
                .filter { it.template.isNotBlank() && it.hour in 0..23 && it.minute in 0..59 }
                .map { RecurringTemplate(it.template, it.hour, it.minute, it.enabled) },
            weeklyDigest = doc.weeklyDigest,
        )
    }

    fun encode(config: AppConfig): String {
        val doc = Doc(
            tagStyles = config.tagStyles.filterValues { it.emoji != null || it.colour != null }
                .mapValues { (_, s) -> StyleDto(s.emoji, s.colour?.name?.lowercase()) },
            reminders = config.reminders.map { ReminderDto(it.id, it.memoRemoteName, it.atEpochMs, it.note) },
            recurring = config.recurring.map { RecurringDto(it.templateTitle, it.hour, it.minute, it.enabled) },
            weeklyDigest = config.weeklyDigest,
        )
        return "MyMemos settings. Edited by the app; safe to leave alone.\n\n#$TAG\n\n```json\n${json.encodeToString(Doc.serializer(), doc)}\n```"
    }
}


