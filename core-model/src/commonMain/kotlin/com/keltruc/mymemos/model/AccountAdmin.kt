package com.keltruc.mymemos.model

import kotlin.time.Instant

data class PersonalAccessToken(
    val name: String,
    val description: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
)

data class Webhook(val name: String, val url: String, val displayName: String, val createTime: Instant)

data class Notification(
    val name: String,
    val senderUsername: String,
    val unread: Boolean,
    val createTime: Instant,
    val type: String,
    /** Server name of the memo the event is about. */
    val memoRemoteName: String,
    val memoSnippet: String,
    val relatedSnippet: String,
)

data class UserStats(
    val totalMemos: Int,
    val links: Int,
    val code: Int,
    val todos: Int,
    val undone: Int,
    val tagCounts: Map<String, Int>,
    val createdTimes: List<Instant>,
)

data class UserPreferences(val locale: String, val defaultVisibility: Visibility)

data class InstanceGeneral(
    val title: String,
    val description: String,
    val disallowRegistration: Boolean,
    val disallowPasswordAuth: Boolean,
    val disallowChangeUsername: Boolean,
    val disallowChangeNickname: Boolean,
    val weekStartDayOffset: Int,
)

data class InstanceStats(val databaseDriver: String, val databaseBytes: Long, val localStorageBytes: Long)
