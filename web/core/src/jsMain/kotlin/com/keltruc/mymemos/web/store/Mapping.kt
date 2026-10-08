package com.keltruc.mymemos.web.store

import com.keltruc.mymemos.data.text.ColourTag
import com.keltruc.mymemos.data.text.Tags
import com.keltruc.mymemos.model.Attachment
import com.keltruc.mymemos.model.Location
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.NoteColour
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.network.dto.AttachmentDto
import com.keltruc.mymemos.network.dto.MemoDto
import com.keltruc.mymemos.network.dto.ReactionDto
import kotlin.time.Instant
import kotlin.uuid.Uuid

// The web's copy of core-data's Mappers.kt, which cannot be shared as it is written against
// Room entities. Keep the two in step.

fun parseEpochMs(rfc3339: String?): Long =
    rfc3339?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0L

fun Long.toRfc3339(): String = Instant.fromEpochMilliseconds(this).toString()

/**
 * Server memo → record. Keeps [existing]'s local id so a re-pull does not change identity,
 * its colour because the server knows nothing about local tints, and its attachments' local
 * ids so cached bytes stay attached.
 */
fun MemoDto.toRecord(accountId: Int, existing: MemoRecord?): MemoRecord {
    val update = parseEpochMs(updateTime)
    val localId = existing?.localId ?: Uuid.random().toString()
    val priorAttachments = existing?.attachments.orEmpty().associateBy { it.remoteName }
    val priorReactions = existing?.reactions.orEmpty().filter { it.remoteName != null }.associateBy { it.remoteName }
    return MemoRecord(
        localId = localId,
        accountId = accountId,
        remoteName = name,
        creator = creator,
        content = content,
        visibility = visibility,
        state = state,
        pinned = pinned,
        tags = tags,
        createTimeEpochMs = parseEpochMs(createTime),
        updateTimeEpochMs = update,
        snippet = snippet,
        hasTaskList = property?.hasTaskList ?: false,
        hasIncompleteTasks = property?.hasIncompleteTasks ?: false,
        hasLink = property?.hasLink ?: false,
        hasCode = property?.hasCode ?: false,
        locationPlaceholder = location?.placeholder,
        latitude = location?.latitude,
        longitude = location?.longitude,
        syncStatus = SyncStatus.SYNCED.name,
        baseUpdateTimeEpochMs = update,
        parent = parent,
        colour = ColourTag.extract(content)?.name ?: existing?.colour,
        // Uploads still in flight have no remote name yet and are kept.
        attachments = attachments.map { it.toRecord(priorAttachments[it.name]?.localId) } +
            existing?.attachments.orEmpty().filter { it.remoteName == null },
        // Reactions not yet pushed are kept, the rest replaced by the server's list.
        reactions = reactions.map { it.toRecord(priorReactions[it.name]?.localId) } +
            existing?.reactions.orEmpty().filter { it.remoteName == null },
        relations = relations.filter { it.memo.name == name }.map { RelationRecord(it.relatedMemo.name, it.relatedMemo.snippet, it.type) },
    )
}

fun AttachmentDto.toRecord(existingLocalId: String?) = AttachmentRecord(
    localId = existingLocalId ?: Uuid.random().toString(),
    remoteName = name,
    filename = filename,
    mimeType = type,
    sizeBytes = size,
    externalLink = externalLink.ifEmpty { null },
    createTimeEpochMs = parseEpochMs(createTime),
)

fun ReactionDto.toRecord(existingLocalId: String?) = ReactionRecord(
    localId = existingLocalId ?: Uuid.random().toString(),
    remoteName = name,
    creator = creator,
    reactionType = reactionType,
    createTimeEpochMs = parseEpochMs(createTime),
)

fun MemoRecord.toModel(): Memo = Memo(
    localId = localId,
    accountId = accountId.toLong(),
    remoteName = remoteName,
    creator = creator,
    content = content,
    visibility = runCatching { Visibility.valueOf(visibility) }.getOrDefault(Visibility.PRIVATE),
    state = runCatching { MemoState.valueOf(state) }.getOrDefault(MemoState.NORMAL),
    pinned = pinned,
    tags = ColourTag.visible(tags),
    createTime = Instant.fromEpochMilliseconds(createTimeEpochMs),
    updateTime = Instant.fromEpochMilliseconds(updateTimeEpochMs),
    snippet = snippet,
    hasTaskList = hasTaskList,
    hasIncompleteTasks = hasIncompleteTasks,
    hasLink = hasLink,
    hasCode = hasCode,
    location = latitude?.let { lat -> longitude?.let { lon -> Location(locationPlaceholder.orEmpty(), lat, lon) } },
    attachments = attachments.map {
        Attachment(it.localId, it.remoteName, localId, it.filename, it.mimeType, it.sizeBytes, it.externalLink, null, Instant.fromEpochMilliseconds(it.createTimeEpochMs))
    },
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.SYNCED),
    parent = parent,
    colour = colour?.let { c -> runCatching { NoteColour.valueOf(c) }.getOrNull() },
)

/** The fields MemoRepository derives from the text on every local write. */
fun MemoRecord.withContent(content: String): MemoRecord = copy(
    content = content,
    tags = Tags.extract(content),
    snippet = content.lineSequence().firstOrNull().orEmpty().take(120),
    hasTaskList = content.contains(anyTask),
    hasIncompleteTasks = content.contains(openTask),
    hasLink = content.contains("http://") || content.contains("https://"),
    hasCode = content.contains("```") || content.contains('`'),
)

private val anyTask = Regex("^\\s*[-*] \\[[ xX]\\] ", RegexOption.MULTILINE)
private val openTask = Regex("^\\s*[-*] \\[ \\] ", RegexOption.MULTILINE)
