package com.keltruc.mymemos.model

import kotlin.time.Instant

enum class Visibility { PRIVATE, PROTECTED, PUBLIC }

enum class MemoState { NORMAL, ARCHIVED }

/**
 * Where a memo's local copy stands relative to the server. Every memo has a
 * client-generated [Memo.localId]; [Memo.remoteName] is filled once the server has it.
 */
enum class SyncStatus { SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE, CONFLICT }

data class Location(
    val placeholder: String,
    val latitude: Double,
    val longitude: Double,
)

data class Memo(
    val localId: String,
    val accountId: Long,
    /** Server resource name, e.g. `memos/AbCdEf`. Null until first push. */
    val remoteName: String?,
    val creator: String?,
    val content: String,
    val visibility: Visibility,
    val state: MemoState,
    val pinned: Boolean,
    val tags: List<String>,
    val createTime: Instant,
    val updateTime: Instant,
    val snippet: String,
    val hasTaskList: Boolean,
    val hasIncompleteTasks: Boolean,
    val hasLink: Boolean,
    val hasCode: Boolean,
    val location: Location?,
    val attachments: List<Attachment>,
    val syncStatus: SyncStatus,
    /** Server name of the memo this comments on, or null. */
    val parent: String? = null,
    val colour: NoteColour? = null,
) {
    val isComment: Boolean get() = parent != null

    /** Content is an end-to-end encrypted blob; see MemoCipher in core-data. */
    val isLocked: Boolean get() = content.trimStart().startsWith(LOCKED_PREFIX)

    /** The plain-text title a locked memo was given, or null if it has none. */
    val lockedTitle: String? get() = if (isLocked) lockedTitleOf(content) else null

    /** The date the timeline is currently ordered on, so what is shown matches what is sorted. */
    fun timelineTime(byModified: Boolean): Instant = if (byModified) updateTime else createTime

    /** Content without the `#colour/x` line, for rendering and editing. */
    val displayContent: String get() = colourLine.replace(content, "").trimEnd('\n')

    companion object {
        const val LOCKED_PREFIX = "mymemos-enc:v1:"

        /**
         * A locked memo may carry a title in the clear, as a `# heading` after the blob:
         *
         *     mymemos-enc:v1:<base64>
         *
         *     # Car insurance
         *
         * After rather than before, because everything that recognises a locked memo looks
         * at how the text starts, and older builds decrypt only the first line.
         */
        fun lockedTitleOf(content: String): String? =
            content.trimStart().lineSequence().drop(1)
                .firstNotNullOfOrNull { lockedTitleLine.matchEntire(it.trim())?.groupValues?.get(1) }
                ?.trim()?.takeIf { it.isNotEmpty() }

        private val lockedTitleLine = Regex("^#\\s+(.+)$")
        private val colourLine = Regex("(?m)^[ \\t]*#colour/[a-z]+[ \\t]*$\\n?")
    }
    val isPendingLocalChange: Boolean get() = syncStatus != SyncStatus.SYNCED
}
