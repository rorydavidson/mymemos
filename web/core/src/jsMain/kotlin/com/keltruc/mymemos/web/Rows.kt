@file:JsExport

package com.keltruc.mymemos.web

import org.khronos.webgl.Uint8Array

// What WebSession hands the TypeScript UI: plain classes of strings, numbers, booleans and
// arrays, the web counterpart of the rows MemosSession hands Swift. Numbers that are Long on
// the other platforms are Double here, because an exported Long arrives in TypeScript as a
// bigint, which nothing on the page wants.

class MemoRow(
    val localId: String,
    /** For a locked memo, its plain-text title, or "Locked memo" if it has none. */
    val title: String,
    val lockedTitle: String?,
    val body: String,
    /** [body] without the line [title] came from, which is what a card shows under it. */
    val bodyBelowTitle: String,
    val tags: Array<String>,
    val pinned: Boolean,
    val locked: Boolean,
    val hasTasks: Boolean,
    val hasOpenTasks: Boolean,
    val attachmentCount: Int,
    /** The memo's tint as 0xRRGGBB, or -1 for none. */
    val colourHex: Int,
    val timeLabel: String,
    val dateLabel: String,
    val visibility: String,
    val archived: Boolean,
    /** Changed here and not yet on the server. */
    val pending: Boolean,
    val conflict: Boolean,
    val hasPlace: Boolean,
    val isoDate: String,
)

class TimelineSection(val key: String, val label: String, val memos: Array<MemoRow>)

class MemoDetail(
    val row: MemoRow,
    /** The text as the editor should show it: no colour line. Empty for a locked memo. */
    val content: String,
    val created: String,
    val updated: String,
    val visibility: String,
    val wordCount: Int,
    val placeName: String?,
    val latitude: Double,
    val longitude: Double,
    val hasPlace: Boolean,
    val bodyBelowTitle: String,
    /** How many lines [bodyBelowTitle] dropped from the front, so a task's line index maps back. */
    val bodyLineOffset: Int,
    val archived: Boolean,
    /** False while the memo is still in the outbox: comments, shares and reminders need a server name. */
    val onServer: Boolean,
    val colourName: String?,
)

class TaskRow(val memoLocalId: String, val lineIndex: Int, val text: String, val dueLabel: String?, val overdue: Boolean)

class TaskGroup(val memoLocalId: String, val memoTitle: String, val tasks: Array<TaskRow>)

class PlacedMemo(val row: MemoRow, val placeName: String, val latitude: Double, val longitude: Double, val dayKey: String)

class Throwback(val row: MemoRow, val whenLabel: String)

class AttachmentRow(
    val localId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Double,
    val isImage: Boolean,
    val uploaded: Boolean,
    /** Set for a link to elsewhere rather than an uploaded file. */
    val externalLink: String?,
)

class ListContinuation(val text: String, val cursor: Int)

class Reveal(val text: String?, val needsPassword: Boolean, val wrongPassword: Boolean)

class ColourOption(val name: String, val hex: Int)

class CommentRow(val localId: String, val creator: String, val mine: Boolean, val body: String, val dateLabel: String, val pending: Boolean)

class ReactionRow(val type: String, val count: Int, val mine: Boolean)

class ReferenceRow(val remoteName: String, val snippet: String, val localId: String?)

class GraphEdge(val from: String, val to: String)

class GraphData(val nodes: Array<MemoRow>, val edges: Array<GraphEdge>)

class ShareRow(val name: String, val url: String, val createdLabel: String, val expiresLabel: String?)

class ShortcutRow(val name: String, val title: String, val filter: String)

class NearbyRow(val row: MemoRow, val placeName: String, val metres: Double)

class SyncStatusRow(
    val pending: Int,
    val failed: Int,
    val conflicts: Int,
    val lastError: String?,
    val authExpired: Boolean,
    val lastSuccessLabel: String,
    val running: Boolean,
)

class FailedOpRow(val id: Double, val memoLocalId: String, val memoTitle: String, val type: String, val error: String, val attempts: Int)

class AccountRow(val id: Int, val serverUrl: String, val username: String, val displayName: String, val active: Boolean, val admin: Boolean)

class TagCount(val tag: String, val count: Int)

class TagStyleRow(val tag: String, val emoji: String?, val colourName: String?, val colourHex: Int)

class DateSuggestionRow(val token: String, val hint: String)

class EmojiGroup(val name: String, val emoji: Array<String>)

class TemplateRow(val id: Int, val title: String, val body: String)

class ReminderRow(
    val id: String,
    val memoRemoteName: String,
    val memoLocalId: String,
    val memoTitle: String,
    val note: String,
    val atEpochMs: Double,
    val whenLabel: String,
    val overdue: Boolean,
)

class RecurringRow(val templateId: Int, val templateTitle: String, val hour: Int, val minute: Int, val enabled: Boolean, val nextLabel: String)

class ProfileRow(val name: String, val username: String, val displayName: String, val email: String, val about: String, val admin: Boolean)

class TokenRow(val name: String, val label: String, val createdLabel: String, val expiresLabel: String, val lastUsedLabel: String, val thisDevice: Boolean)

class WebhookRow(val name: String, val displayName: String, val url: String, val createdLabel: String)

class NotificationRow(
    val name: String,
    val sender: String,
    val unread: Boolean,
    val dateLabel: String,
    val type: String,
    val memoRemoteName: String,
    val memoSnippet: String,
    val relatedSnippet: String,
)

class StatsRow(val totalMemos: Int, val links: Int, val code: Int, val todos: Int, val undone: Int, val tagCounts: Array<TagCount>, val activeDays: Int)

class UserRow(val name: String, val username: String, val displayName: String, val email: String, val admin: Boolean, val archived: Boolean)

class InstanceRow(
    val title: String,
    val about: String,
    val disallowRegistration: Boolean,
    val disallowPasswordAuth: Boolean,
    val disallowChangeUsername: Boolean,
    val disallowChangeNickname: Boolean,
    val weekStartDayOffset: Int,
)

class InstanceStatsRow(val databaseDriver: String, val databaseBytes: Double, val localStorageBytes: Double)

class ImportResultRow(val imported: Int, val skipped: Int, val duplicates: Int, val failures: Array<String>)

/** A file the user picked, already read by the page. */
class PickedFile(val name: String, val bytes: Uint8Array, val modifiedEpochMs: Double?)

/** Something due now that the page should announce: a reminder, or the weekly digest. */
class DueNotice(val id: String, val title: String, val body: String, val memoLocalId: String?)
