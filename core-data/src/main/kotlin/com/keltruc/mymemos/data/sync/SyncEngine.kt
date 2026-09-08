package com.keltruc.mymemos.data.sync

import android.util.Base64
import androidx.room.withTransaction
import com.keltruc.mymemos.data.attachments.AttachmentStore
import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.data.widget.WidgetRefresher
import com.keltruc.mymemos.data.mapper.relationEntities
import com.keltruc.mymemos.data.mapper.toEntity
import com.keltruc.mymemos.data.mapper.toRfc3339
import com.keltruc.mymemos.database.MyMemosDatabase
import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.database.dao.AttachmentDao
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.database.dao.PendingOpDao
import com.keltruc.mymemos.database.dao.ReactionDao
import com.keltruc.mymemos.database.dao.RelationDao
import com.keltruc.mymemos.database.dao.ShortcutDao
import com.keltruc.mymemos.database.entity.AccountEntity
import com.keltruc.mymemos.database.entity.MemoEntity
import com.keltruc.mymemos.database.entity.PendingOpEntity
import com.keltruc.mymemos.database.entity.PendingOpEntity.Type
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.network.ApiException
import com.keltruc.mymemos.network.api.MemosApi
import com.keltruc.mymemos.network.dto.AttachmentCreateDto
import com.keltruc.mymemos.network.dto.AttachmentRefDto
import com.keltruc.mymemos.network.dto.LocationDto
import com.keltruc.mymemos.network.dto.MemoRefDto
import com.keltruc.mymemos.network.dto.MemoRelationWriteDto
import com.keltruc.mymemos.network.dto.ReactionWriteDto
import com.keltruc.mymemos.network.dto.SetMemoRelationsRequestDto
import com.keltruc.mymemos.network.dto.UpsertReactionRequestDto
import com.keltruc.mymemos.network.dto.MemoDto
import com.keltruc.mymemos.network.dto.MemoWriteDto
import com.keltruc.mymemos.network.dto.SetMemoAttachmentsRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push the outbox, then pull changes, then reconcile. Safe to call from a worker and from
 * the UI; a mutex keeps two runs from interleaving.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val db: MyMemosDatabase,
    private val accountDao: AccountDao,
    private val memoDao: MemoDao,
    private val attachmentDao: AttachmentDao,
    private val pendingOpDao: PendingOpDao,
    private val relationDao: RelationDao,
    private val reactionDao: ReactionDao,
    private val shortcutDao: ShortcutDao,
    private val registry: ApiClientRegistry,
    private val attachmentStore: AttachmentStore,
    private val widgets: WidgetRefresher,
    private val json: Json,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    sealed interface Outcome {
        data object Success : Outcome
        data class Retry(val cause: Throwable) : Outcome
        data class AuthFailed(val cause: Throwable) : Outcome
    }

    suspend fun sync(accountId: Long, fullPull: Boolean = false): Outcome = withContext(Dispatchers.IO) { mutex.withLock {
        val account = accountDao.getById(accountId) ?: return@withLock Outcome.Success
        val api = registry.api(account.serverUrl, account.userResourceName)
        _state.update { it.copy(running = true, lastError = null, authExpired = false) }
        try {
            push(account, api)
            pull(account, api, fullPull)
            runCatching { pullShortcuts(account, api) }
            _state.update { it.copy(running = false, lastSuccess = Instant.now()) }
            widgets.refresh()
            Outcome.Success
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            android.util.Log.w(TAG, "sync failed for account $accountId", e)
            val authFailed = e is ApiException && e.isUnauthenticated
            _state.update { it.copy(running = false, lastError = message, authExpired = authFailed) }
            if (authFailed) Outcome.AuthFailed(e) else Outcome.Retry(e)
        }
    } }

    // ---- push ------------------------------------------------------------------------

    private suspend fun push(account: AccountEntity, api: MemosApi) {
        for (op in pendingOpDao.queued(account.id)) {
            try {
                applyOp(account, api, op)
                pendingOpDao.delete(op.id)
            } catch (e: ApiException) {
                if (e.isUnauthenticated) throw e
                // Server refused this op; park it rather than block everything behind it.
                pendingOpDao.update(op.copy(attempts = op.attempts + 1, lastError = e.message, failed = e.httpStatus in 400..499))
                if (e.httpStatus !in 400..499) throw e
            } catch (e: IOException) {
                pendingOpDao.update(op.copy(attempts = op.attempts + 1, lastError = e.message))
                throw e
            }
        }
    }

    private suspend fun applyOp(account: AccountEntity, api: MemosApi, op: PendingOpEntity) {
        val memo = memoDao.getByLocalId(op.memoLocalId)
        when (op.type) {
            Type.CREATE -> {
                memo ?: return
                if (memo.remoteName != null) return // already created by an earlier attempt
                val created = call {
                    api.createMemo(
                        MemoWriteDto(
                            content = memo.content,
                            visibility = memo.visibility,
                            pinned = memo.pinned.takeIf { it },
                            createTime = Instant.ofEpochMilli(memo.createTimeEpochMs).toRfc3339(),
                        ),
                    )
                }
                absorbServerMemo(memo, created, keepPending = pendingOpDao.countForMemo(memo.localId) > 1)
            }
            Type.UPDATE_CONTENT -> {
                val name = memo?.remoteName ?: return
                val server = call { api.getMemo(name) }
                val serverUpdated = parseEpoch(server.updateTime)
                val base = memo.baseUpdateTimeEpochMs ?: 0L
                if (serverUpdated <= base) {
                    val updated = call { api.updateMemo(name, MemoWriteDto(content = memo.content), "content,update_time") }
                    absorbServerMemo(memo, updated, keepPending = pendingOpDao.countForMemo(memo.localId) > 1)
                    return
                }
                val baseContent = json.decodeFromString<UpdatePayload>(op.payloadJson.ifEmpty { "{}" }).baseContent
                    ?: server.content
                when (val result = ThreeWayMerge.merge(baseContent, memo.content, server.content)) {
                    is ThreeWayMerge.Result.Merged -> {
                        val updated = call { api.updateMemo(name, MemoWriteDto(content = result.text), "content,update_time") }
                        absorbServerMemo(memo, updated, keepPending = pendingOpDao.countForMemo(memo.localId) > 1)
                    }
                    ThreeWayMerge.Result.Conflict -> forkConflict(account, memo, server)
                }
            }
            Type.SET_PINNED -> {
                val name = memo?.remoteName ?: return
                val updated = call { api.updateMemo(name, MemoWriteDto(pinned = memo.pinned), "pinned,update_time") }
                absorbServerMemo(memo, updated, keepPending = pendingOpDao.countForMemo(memo.localId) > 1)
            }
            Type.SET_VISIBILITY -> {
                val name = memo?.remoteName ?: return
                val updated = call { api.updateMemo(name, MemoWriteDto(visibility = memo.visibility), "visibility,update_time") }
                absorbServerMemo(memo, updated, keepPending = pendingOpDao.countForMemo(memo.localId) > 1)
            }
            Type.SET_STATE -> {
                val name = memo?.remoteName ?: return
                val updated = call { api.updateMemo(name, MemoWriteDto(state = memo.state), "state,update_time") }
                absorbServerMemo(memo, updated, keepPending = pendingOpDao.countForMemo(memo.localId) > 1)
            }
            Type.DELETE -> {
                val name = json.decodeFromString<DeletePayload>(op.payloadJson).remoteName
                try {
                    call { api.deleteMemo(name) }
                } catch (e: ApiException) {
                    if (!e.isNotFound) throw e
                }
                memoDao.deleteByLocalId(op.memoLocalId)
            }
            Type.ADD_ATTACHMENT -> {
                val name = memo?.remoteName ?: return
                val payload = json.decodeFromString<AttachmentPayload>(op.payloadJson)
                val local = attachmentDao.byLocalId(payload.attachmentLocalId) ?: return
                if (local.remoteName != null) return
                val bytes = attachmentStore.file(local.localId).readBytes()
                val created = call {
                    api.createAttachment(
                        AttachmentCreateDto(
                            filename = local.filename,
                            type = local.mimeType,
                            content = Base64.encodeToString(bytes, Base64.NO_WRAP),
                            memo = name,
                        ),
                    )
                }
                attachmentDao.update(local.copy(remoteName = created.name, sizeBytes = created.size))
                syncAttachmentSet(api, memo)
            }
            Type.REMOVE_ATTACHMENT -> {
                val name = memo?.remoteName ?: return
                val payload = json.decodeFromString<AttachmentPayload>(op.payloadJson)
                syncAttachmentSet(api, memo)
                payload.remoteName?.let { remote ->
                    try {
                        call { api.deleteAttachment(remote) }
                    } catch (e: ApiException) {
                        if (!e.isNotFound) throw e
                    }
                }
                attachmentStore.delete(payload.attachmentLocalId)
            }
            Type.CREATE_COMMENT -> {
                memo ?: return
                if (memo.remoteName != null) return
                val parentName = memo.parent ?: return
                val created = call {
                    api.createMemoComment(
                        parentName,
                        MemoWriteDto(
                            content = memo.content,
                            visibility = memo.visibility,
                            createTime = Instant.ofEpochMilli(memo.createTimeEpochMs).toRfc3339(),
                        ),
                    )
                }
                absorbServerMemo(memo, created, keepPending = false)
            }
            Type.UPSERT_REACTION -> {
                val name = memo?.remoteName ?: return
                val payload = json.decodeFromString<ReactionPayload>(op.payloadJson)
                val local = reactionDao.byLocalId(payload.reactionLocalId) ?: return
                if (local.remoteName != null) return
                val created = call {
                    api.upsertMemoReaction(name, UpsertReactionRequestDto(ReactionWriteDto(contentId = name, reactionType = local.reactionType)))
                }
                reactionDao.upsertAll(listOf(created.toEntity(memo.localId, local.localId)))
            }
            Type.DELETE_REACTION -> {
                val payload = json.decodeFromString<ReactionPayload>(op.payloadJson)
                payload.remoteName?.let { remote ->
                    try {
                        call { api.deleteMemoReaction(remote) }
                    } catch (e: ApiException) {
                        if (!e.isNotFound) throw e
                    }
                }
            }
            Type.SET_RELATIONS -> {
                val name = memo?.remoteName ?: return
                val refs = relationDao.references(memo.localId).map {
                    MemoRelationWriteDto(memo = MemoRefDto(name), relatedMemo = MemoRefDto(it.relatedRemoteName), type = "REFERENCE")
                }
                call { api.setMemoRelations(name, SetMemoRelationsRequestDto(refs)) }
            }
            Type.SET_LOCATION -> {
                val name = memo?.remoteName ?: return
                val location = memo.latitude?.let { lat ->
                    memo.longitude?.let { lon -> LocationDto(memo.locationPlaceholder.orEmpty(), lat, lon) }
                }
                val updated = call { api.updateMemo(name, MemoWriteDto(location = location), "location,update_time") }
                absorbServerMemo(memo, updated, keepPending = pendingOpDao.countForMemo(memo.localId) > 1)
            }
        }
    }

    /** Tells the server the full attachment list as we hold it locally. */
    private suspend fun syncAttachmentSet(api: MemosApi, memo: MemoEntity) {
        val name = memo.remoteName ?: return
        val refs = attachmentDao.forMemo(memo.localId).mapNotNull { it.remoteName }.map { AttachmentRefDto(it) }
        call { api.setMemoAttachments(name, SetMemoAttachmentsRequestDto(refs)) }
    }

    /**
     * Server disagrees and the texts cannot be merged. Keep the server's version under the
     * existing row and park the local text as a new conflict memo so nothing is lost.
     */
    private suspend fun forkConflict(account: AccountEntity, local: MemoEntity, server: MemoDto) {
        db.withTransaction {
            val forkId = UUID.randomUUID().toString()
            val fork = local.copy(
                localId = forkId,
                remoteName = null,
                content = local.content.trimEnd() + "\n\n#conflict",
                tagsJoined = (local.tagsJoined.split(MemoEntity.TAG_SEPARATOR).filter { it.isNotEmpty() } + "conflict")
                    .distinct().joinToString(MemoEntity.TAG_SEPARATOR),
                createTimeEpochMs = System.currentTimeMillis(),
                updateTimeEpochMs = System.currentTimeMillis(),
                syncStatus = SyncStatus.CONFLICT.name,
                baseUpdateTimeEpochMs = null,
            )
            memoDao.upsert(fork)
            pendingOpDao.insert(PendingOpEntity(accountId = account.id, memoLocalId = forkId, type = Type.CREATE))
            memoDao.upsert(server.toEntity(account.id, local.localId))
        }
    }

    private suspend fun absorbServerMemo(local: MemoEntity, server: MemoDto, keepPending: Boolean) {
        db.withTransaction {
            // CreateMemoComment's response does not echo `parent`; keep what we know locally.
            val entity = server.toEntity(local.accountId, local.localId).copy(parent = server.parent ?: local.parent).let {
                if (keepPending) {
                    // Later ops still queued: keep local content so they push the right thing.
                    it.copy(
                        content = local.content,
                        pinned = local.pinned,
                        visibility = local.visibility,
                        state = local.state,
                        syncStatus = SyncStatus.PENDING_UPDATE.name,
                    )
                } else {
                    it
                }
            }
            memoDao.upsert(entity)
            reconcileAttachments(entity.localId, server)
            reconcileSocial(entity.localId, server)
        }
    }

    // ---- pull ------------------------------------------------------------------------

    private suspend fun pull(account: AccountEntity, api: MemosApi, full: Boolean) {
        val since = account.lastSyncEpochMs?.takeUnless { full }
        val startedAt = System.currentTimeMillis()
        val fetched = mutableListOf<MemoDto>()
        for (state in listOf("NORMAL", "ARCHIVED")) {
            var token: String? = null
            do {
                val page = call {
                    api.listMemos(
                        pageSize = 200,
                        pageToken = token,
                        state = state,
                        orderBy = "update_time desc",
                        // Small overlap so clock skew between phone and server cannot drop an edit.
                        filter = since?.let { "updated_ts > timestamp(\"${Instant.ofEpochMilli(it - 60_000).toRfc3339()}\")" },
                    )
                }
                fetched += page.memos
                token = page.nextPageToken.ifEmpty { null }
            } while (token != null)
        }

        db.withTransaction {
            for (dto in fetched) {
                val existing = memoDao.getByRemoteName(account.id, dto.name)
                // A row with queued ops is ahead of the server; leave it for the next push.
                if (existing != null && (existing.syncStatus != SyncStatus.SYNCED.name || pendingOpDao.countForMemo(existing.localId) > 0)) continue
                val entity = dto.toEntity(account.id, existing?.localId)
                memoDao.upsert(entity)
                reconcileAttachments(entity.localId, dto)
                reconcileSocial(entity.localId, dto)
            }
            if (since == null) {
                val keep = fetched.map { it.name }.ifEmpty { listOf("") }
                memoDao.deleteSyncedNotIn(account.id, keep)
            }
            accountDao.setLastSync(account.id, startedAt)
        }
    }

    /** Replaces synced relations and reactions with the server's list; queued local ones stay. */
    suspend fun reconcileSocial(memoLocalId: String, dto: MemoDto) {
        relationDao.deleteForMemo(memoLocalId)
        relationDao.upsertAll(dto.relationEntities(memoLocalId))
        val existing = reactionDao.forMemo(memoLocalId).filter { it.remoteName != null }.associateBy { it.remoteName }
        reactionDao.deleteSyncedForMemo(memoLocalId)
        reactionDao.upsertAll(dto.reactions.map { it.toEntity(memoLocalId, existing[it.name]?.localId) })
    }

    private suspend fun pullShortcuts(account: AccountEntity, api: MemosApi) {
        val shortcuts = call { api.listShortcuts(account.userResourceName) }.shortcuts
        shortcutDao.replaceAll(account.id, shortcuts.map { it.toEntity(account.id) })
    }

    private suspend fun reconcileAttachments(memoLocalId: String, dto: MemoDto) {
        val existing = attachmentDao.forMemo(memoLocalId).associateBy { it.remoteName }
        val fromServer = dto.attachments.map { a ->
            val prior = existing[a.name]
            a.toEntity(memoLocalId, prior?.localId).copy(localPath = prior?.localPath)
        }
        // Keep attachments that are still uploading (no remote name yet).
        val stillLocal = attachmentDao.forMemo(memoLocalId).filter { it.remoteName == null }
        attachmentDao.deleteForMemo(memoLocalId)
        attachmentDao.upsertAll(fromServer + stillLocal)
    }

    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: HttpException) {
        throw ApiException.from(e, json)
    }

    private fun parseEpoch(rfc3339: String?): Long =
        rfc3339?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private companion object {
        const val TAG = "SyncEngine"
    }

    @kotlinx.serialization.Serializable
    data class UpdatePayload(val baseContent: String? = null)

    @kotlinx.serialization.Serializable
    data class DeletePayload(val remoteName: String)

    @kotlinx.serialization.Serializable
    data class AttachmentPayload(val attachmentLocalId: String, val remoteName: String? = null)

    @kotlinx.serialization.Serializable
    data class ReactionPayload(val reactionLocalId: String, val remoteName: String? = null)
}
