package com.keltruc.mymemos.data.mapper

import com.keltruc.mymemos.database.dao.MemoWithAttachments
import com.keltruc.mymemos.database.entity.AccountEntity
import com.keltruc.mymemos.database.entity.AttachmentEntity
import com.keltruc.mymemos.database.entity.MemoEntity
import com.keltruc.mymemos.database.entity.MemoRelationEntity
import com.keltruc.mymemos.database.entity.ReactionEntity
import com.keltruc.mymemos.database.entity.ShortcutEntity
import com.keltruc.mymemos.database.entity.TemplateEntity
import com.keltruc.mymemos.data.text.ColourTag
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Attachment
import com.keltruc.mymemos.model.AuthMethod
import com.keltruc.mymemos.model.Location
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoShare
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.NoteColour
import com.keltruc.mymemos.model.Template
import com.keltruc.mymemos.model.Reaction
import com.keltruc.mymemos.model.Reference
import com.keltruc.mymemos.model.Shortcut
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.User
import com.keltruc.mymemos.model.UserRole
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.network.dto.AttachmentDto
import com.keltruc.mymemos.network.dto.MemoDto
import com.keltruc.mymemos.network.dto.MemoShareDto
import com.keltruc.mymemos.network.dto.ReactionDto
import com.keltruc.mymemos.network.dto.ShortcutDto
import com.keltruc.mymemos.network.dto.UserDto
import java.time.Instant
import java.util.UUID

fun parseInstant(rfc3339: String?): Instant =
    rfc3339?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH

fun Instant.toRfc3339(): String = toString()

fun UserDto.toModel() = User(
    name = name,
    username = username,
    displayName = displayName,
    email = email,
    avatarUrl = avatarUrl,
    description = description,
    role = if (role == "ADMIN") UserRole.ADMIN else UserRole.USER,
)

fun AccountEntity.toModel() = Account(
    id = id,
    serverUrl = serverUrl,
    userResourceName = userResourceName,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    role = if (role == "ADMIN") UserRole.ADMIN else UserRole.USER,
    authMethod = runCatching { AuthMethod.valueOf(authMethod) }.getOrDefault(AuthMethod.PASSWORD),
    serverVersion = serverVersion,
)

/**
 * Server memo → entity. Keeps [existingLocalId] so a re-pull does not change identity, and
 * [existingColour] because the server knows nothing about local tints.
 */
fun MemoDto.toEntity(accountId: Long, existingLocalId: String? = null, existingColour: String? = null): MemoEntity {
    val update = parseInstant(updateTime)
    return MemoEntity(
        localId = existingLocalId ?: UUID.randomUUID().toString(),
        accountId = accountId,
        remoteName = name,
        creator = creator,
        content = content,
        visibility = visibility,
        state = state,
        pinned = pinned,
        tagsJoined = tags.joinToString(MemoEntity.TAG_SEPARATOR),
        createTimeEpochMs = parseInstant(createTime).toEpochMilli(),
        updateTimeEpochMs = update.toEpochMilli(),
        snippet = snippet,
        hasTaskList = property?.hasTaskList ?: false,
        hasIncompleteTasks = property?.hasIncompleteTasks ?: false,
        hasLink = property?.hasLink ?: false,
        hasCode = property?.hasCode ?: false,
        locationPlaceholder = location?.placeholder,
        latitude = location?.latitude,
        longitude = location?.longitude,
        syncStatus = SyncStatus.SYNCED.name,
        baseUpdateTimeEpochMs = update.toEpochMilli(),
        parent = parent,
        colour = ColourTag.extract(content)?.name ?: existingColour,
    )
}

fun MemoDto.relationEntities(memoLocalId: String): List<MemoRelationEntity> = relations
    .filter { it.memo.name == name }
    .map { MemoRelationEntity(memoLocalId, it.relatedMemo.name, it.relatedMemo.snippet, it.type) }

fun ReactionDto.toEntity(memoLocalId: String, existingLocalId: String? = null) = ReactionEntity(
    localId = existingLocalId ?: UUID.randomUUID().toString(),
    memoLocalId = memoLocalId,
    remoteName = name,
    creator = creator,
    reactionType = reactionType,
    createTimeEpochMs = parseInstant(createTime).toEpochMilli(),
)

fun ReactionEntity.toModel() = Reaction(localId, remoteName, creator, reactionType, Instant.ofEpochMilli(createTimeEpochMs))
fun MemoRelationEntity.toReference() = Reference(relatedRemoteName, relatedSnippet)
fun ShortcutEntity.toModel() = Shortcut(name, title, filter)
fun ShortcutDto.toEntity(accountId: Long) = ShortcutEntity(accountId, name, title, filter)

fun MemoShareDto.toModel(serverUrl: String) = MemoShare(
    name = name,
    url = "${serverUrl.trimEnd('/')}/memos/shares/${name.substringAfterLast('/')}",
    createTime = parseInstant(createTime),
    expireTime = expireTime?.let { parseInstant(it) },
)

fun AttachmentDto.toEntity(memoLocalId: String, existingLocalId: String? = null) = AttachmentEntity(
    localId = existingLocalId ?: UUID.randomUUID().toString(),
    memoLocalId = memoLocalId,
    remoteName = name,
    filename = filename,
    mimeType = type,
    sizeBytes = size,
    externalLink = externalLink.ifEmpty { null },
    localPath = null,
    createTimeEpochMs = parseInstant(createTime).toEpochMilli(),
)

fun AttachmentEntity.toModel() = Attachment(
    localId = localId,
    remoteName = remoteName,
    memoLocalId = memoLocalId,
    filename = filename,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    externalLink = externalLink,
    localPath = localPath,
    createTime = Instant.ofEpochMilli(createTimeEpochMs),
)

fun MemoWithAttachments.toModel(): Memo = memo.toModel(attachments.map { it.toModel() })

fun MemoEntity.toModel(attachments: List<Attachment> = emptyList()) = Memo(
    localId = localId,
    accountId = accountId,
    remoteName = remoteName,
    creator = creator,
    content = content,
    visibility = runCatching { Visibility.valueOf(visibility) }.getOrDefault(Visibility.PRIVATE),
    state = runCatching { MemoState.valueOf(state) }.getOrDefault(MemoState.NORMAL),
    pinned = pinned,
    tags = if (tagsJoined.isEmpty()) emptyList() else tagsJoined.split(MemoEntity.TAG_SEPARATOR).filter { !ColourTag.isColourTag(it) },
    createTime = Instant.ofEpochMilli(createTimeEpochMs),
    updateTime = Instant.ofEpochMilli(updateTimeEpochMs),
    snippet = snippet,
    hasTaskList = hasTaskList,
    hasIncompleteTasks = hasIncompleteTasks,
    hasLink = hasLink,
    hasCode = hasCode,
    location = latitude?.let { lat -> longitude?.let { lon -> Location(locationPlaceholder.orEmpty(), lat, lon) } },
    attachments = attachments,
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.SYNCED),
    parent = parent,
    colour = colour?.let { c -> runCatching { NoteColour.valueOf(c) }.getOrNull() },
)

fun TemplateEntity.toModel() = Template(id, title, body)

/** Server URL for an uploaded attachment; null while it is still local-only. */
fun Attachment.remoteUrl(serverUrl: String, thumbnail: Boolean = false): String? {
    externalLink?.let { return it }
    val name = remoteName ?: return null
    val base = "${serverUrl.trimEnd('/')}/file/$name/${java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20")}"
    return if (thumbnail) "$base?thumbnail=true" else base
}
