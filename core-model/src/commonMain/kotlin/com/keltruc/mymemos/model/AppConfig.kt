package com.keltruc.mymemos.model

/** Per-account settings that must follow the user across devices. Lives in a hidden memo. */
data class AppConfig(
    val tagStyles: Map<String, TagStyle> = emptyMap(),
    val reminders: List<Reminder> = emptyList(),
    val recurring: List<RecurringTemplate> = emptyList(),
    val weeklyDigest: Boolean = false,
)

data class TagStyle(val emoji: String? = null, val colour: NoteColour? = null)

/** A one-off reminder for a memo, keyed by the memo's server name. */
data class Reminder(val id: String, val memoRemoteName: String, val atEpochMs: Long, val note: String = "")

/** Create a memo from [templateTitle] every day at [hour]:[minute] unless one exists already. */
data class RecurringTemplate(val templateTitle: String, val hour: Int, val minute: Int, val enabled: Boolean = true)
