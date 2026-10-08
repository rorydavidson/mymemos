package com.keltruc.mymemos.web.sync

import com.keltruc.mymemos.data.sync.SyncState
import com.keltruc.mymemos.data.sync.ThreeWayMerge
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.network.ApiException
import com.keltruc.mymemos.network.api.MemosApi
import com.keltruc.mymemos.network.dto.AttachmentCreateDto
import com.keltruc.mymemos.network.dto.AttachmentRefDto
import com.keltruc.mymemos.network.dto.LocationDto
import com.keltruc.mymemos.network.dto.MemoDto
import com.keltruc.mymemos.network.dto.MemoRefDto
import com.keltruc.mymemos.network.dto.MemoRelationWriteDto
import com.keltruc.mymemos.network.dto.MemoWriteDto
import com.keltruc.mymemos.network.dto.ReactionWriteDto
import com.keltruc.mymemos.network.dto.SetMemoAttachmentsRequestDto
import com.keltruc.mymemos.network.dto.SetMemoRelationsRequestDto
import com.keltruc.mymemos.network.dto.UpsertReactionRequestDto
import com.keltruc.mymemos.web.store.AccountRecord
import com.keltruc.mymemos.web.store.MemoRecord
import com.keltruc.mymemos.web.store.OpRecord
import com.keltruc.mymemos.web.store.ShortcutRecord
import com.keltruc.mymemos.web.store.WebStore
import com.keltruc.mymemos.web.store.parseEpochMs
import com.keltruc.mymemos.web.store.toRecord
import com.keltruc.mymemos.web.store.toRfc3339
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Push the outbox, then pull changes, then reconcile: core-data's SyncEngine, step for step,
 * over [WebStore] instead of Room. The decisions that matter most (the merge, tags, colours)
 * are the shared code itself; what is here is the order of operations, and it should stay a
 * line-by-line match for the original. When one changes, change the other.
 */
class WebSyncEngine(
    private val store: WebStore,
    private val apis: (AccountRecord) -> MemosApi,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var state: SyncState = SyncState()
        private set

    sealed interface Outcome {
        data object Success : Outcome
        data class Retry(val cause: Throwable) : Outcome
        data class AuthFailed(val cause: Throwable) : Outcome
    }

    suspend fun sync(accountId: Int, fullPull: Boolean = false): Outcome = mutex.withLock {
        val account = store.account(accountId) ?: return@withLock Outcome.Success
        val api = apis(account)
        state = state.copy(running = true, lastError = null, authExpired = false)
        try {
            push(account, api)
            pull(account, api, fullPull)
            runCatching { pullShortcuts(account, api) }
            runCatching { refreshProfile(account, api) }
            state = state.copy(running = false, lastSuccess = Clock.System.now())
            store.write { } // tell listeners the run is over
            Outcome.Success
        } catch (e: Throwable) {
            val message = e.message ?: (e::class.simpleName ?: "error")
            val authFailed = e is ApiException && e.isUnauthenticated
            state = state.copy(running = false, lastError = message, authExpired = authFailed)
            store.write { }
            if (authFailed) Outcome.AuthFailed(e) else Outcome.Retry(e)
        }
    }

    // ---- push ------------------------------------------------------------------------

    private suspend fun push(account: AccountRecord, api: MemosApi) {
        // CREATE ops go first so a memo exists before anything that hangs off it.
        while (true) {
            val op = store.queued(account.id).sortedWith(compareBy({ it.type != OpRecord.CREATE }, { it.id })).firstOrNull() ?: break
            try {
                applyOp(account, api, op)
                store.write { deleteOp(op.id) }
            } catch (e: ApiException) {
                if (e.isUnauthenticated) throw e
                if (e.isNotFound && recoverMissingRemote(account, op)) continue
                // Server refused this op; park it rather than block everything behind it.
                store.write { updateOp(op.copy(attempts = op.attempts + 1, lastError = e.message, failed = e.httpStatus in 400..499)) }
                if (e.httpStatus !in 400..499) throw e
            } catch (e: Throwable) {
                // In a browser a failed fetch is all there is to go on: keep the op, retry later.
                store.write { updateOp(op.copy(attempts = op.attempts + 1, lastError = e.message)) }
                throw e
            }
        }
    }

    /**
     * The server no longer has the memo an op targets. Rather than lose the local edits, turn
     * the row back into a brand-new memo and let the outbox create it. Comments on a vanished
     * parent are dropped.
     */
    private suspend fun recoverMissingRemote(account: AccountRecord, op: OpRecord): Boolean {
        val memo = store.memo(op.memoLocalId) ?: return false
        if (op.type == OpRecord.DELETE || op.type == OpRecord.DELETE_REACTION || memo.remoteName == null) return false
        val held = memo.attachments.filter { it.remoteName == null || store.readBlob(it.localId) != null }.map { it.localId }.toSet()
        store.write {
            deleteOpsForMemo(memo.localId)
            if (memo.parent != null) {
                deleteMemo(memo.localId)
                return@write
            }
            val kept = memo.attachments.filter { it.localId in held }.map { it.copy(remoteName = null) }
            upsertMemo(
                memo.copy(
                    remoteName = null, syncStatus = SyncStatus.PENDING_CREATE.name, baseUpdateTimeEpochMs = null,
                    attachments = kept, reactions = memo.reactions.filter { it.remoteName == null },
                ),
            )
            insertOp(OpRecord(0, account.id, memo.localId, OpRecord.CREATE))
            for (a in kept) {
                insertOp(OpRecord(0, account.id, memo.localId, OpRecord.ADD_ATTACHMENT, json.encodeToString(AttachmentPayload.serializer(), AttachmentPayload(a.localId))))
            }
            if (memo.relations.isNotEmpty()) insertOp(OpRecord(0, account.id, memo.localId, OpRecord.SET_RELATIONS))
        }
        return true
    }

    private suspend fun applyOp(account: AccountRecord, api: MemosApi, op: OpRecord) {
        val memo = store.memo(op.memoLocalId)
        when (op.type) {
            OpRecord.CREATE -> {
                memo ?: return
                if (memo.remoteName != null) return // already created by an earlier attempt
                val created = call {
                    api.createMemo(
                        MemoWriteDto(
                            content = memo.content,
                            visibility = memo.visibility,
                            pinned = memo.pinned.takeIf { it },
                            createTime = memo.createTimeEpochMs.toRfc3339(),
                        ),
                    )
                }
                absorbServerMemo(memo, created, keepPending = store.countForMemo(memo.localId) > 1)
            }
            OpRecord.UPDATE_CONTENT -> {
                val name = memo?.remoteName ?: return
                val server = call { api.getMemo(name) }
                val serverUpdated = parseEpochMs(server.updateTime)
                val base = memo.baseUpdateTimeEpochMs ?: 0L
                if (serverUpdated <= base) {
                    val updated = call { api.updateMemo(name, MemoWriteDto(content = memo.content), "content,update_time") }
                    absorbServerMemo(memo, updated, keepPending = store.countForMemo(memo.localId) > 1)
                    return
                }
                val baseContent = json.decodeFromString(UpdatePayload.serializer(), op.payloadJson.ifEmpty { "{}" }).baseContent
                    ?: server.content
                when (val result = ThreeWayMerge.merge(baseContent, memo.content, server.content)) {
                    is ThreeWayMerge.Result.Merged -> {
                        val updated = call { api.updateMemo(name, MemoWriteDto(content = result.text), "content,update_time") }
                        absorbServerMemo(memo, updated, keepPending = store.countForMemo(memo.localId) > 1)
                    }
                    ThreeWayMerge.Result.Conflict -> forkConflict(account, memo, server)
                }
            }
            OpRecord.SET_PINNED -> {
                val name = memo?.remoteName ?: return
                val updated = call { api.updateMemo(name, MemoWriteDto(pinned = memo.pinned), "pinned,update_time") }
                absorbServerMemo(memo, updated, keepPending = store.countForMemo(memo.localId) > 1)
            }
            OpRecord.SET_VISIBILITY -> {
                val name = memo?.remoteName ?: return
                val updated = call { api.updateMemo(name, MemoWriteDto(visibility = memo.visibility), "visibility,update_time") }
                absorbServerMemo(memo, updated, keepPending = store.countForMemo(memo.localId) > 1)
            }
            OpRecord.SET_STATE -> {
                val name = memo?.remoteName ?: return
                val updated = call { api.updateMemo(name, MemoWriteDto(state = memo.state), "state,update_time") }
                absorbServerMemo(memo, updated, keepPending = store.countForMemo(memo.localId) > 1)
            }
            OpRecord.DELETE -> {
                val name = json.decodeFromString(DeletePayload.serializer(), op.payloadJson).remoteName
                try {
                    call { api.deleteMemo(name) }
                } catch (e: ApiException) {
                    if (!e.isNotFound) throw e
                }
                // The bytes were kept so the delete could be taken back; it cannot now.
                memo?.attachments?.forEach { store.deleteBlob(it.localId) }
                store.write { deleteMemo(op.memoLocalId) }
            }
            OpRecord.ADD_ATTACHMENT -> {
                val name = memo?.remoteName ?: return
                val payload = json.decodeFromString(AttachmentPayload.serializer(), op.payloadJson)
                val local = memo.attachments.firstOrNull { it.localId == payload.attachmentLocalId } ?: return
                if (local.remoteName != null) return
                val bytes = store.readBlob(local.localId) ?: return
                val created = call {
                    api.createAttachment(AttachmentCreateDto(filename = local.filename, type = local.mimeType, content = Base64.Default.encode(bytes), memo = name))
                }
                val fresh = store.memo(memo.localId) ?: return
                val next = fresh.copy(attachments = fresh.attachments.map { if (it.localId == local.localId) it.copy(remoteName = created.name, sizeBytes = created.size) else it })
                store.write { upsertMemo(next) }
                syncAttachmentSet(api, next)
            }
            OpRecord.REMOVE_ATTACHMENT -> {
                memo?.remoteName ?: return
                val payload = json.decodeFromString(AttachmentPayload.serializer(), op.payloadJson)
                syncAttachmentSet(api, memo)
                payload.remoteName?.let { remote ->
                    try {
                        call { api.deleteAttachment(remote) }
                    } catch (e: ApiException) {
                        if (!e.isNotFound) throw e
                    }
                }
                store.deleteBlob(payload.attachmentLocalId)
            }
            OpRecord.CREATE_COMMENT -> {
                memo ?: return
                if (memo.remoteName != null) return
                val parentName = memo.parent ?: return
                val created = call {
                    api.createMemoComment(parentName, MemoWriteDto(content = memo.content, visibility = memo.visibility, createTime = memo.createTimeEpochMs.toRfc3339()))
                }
                absorbServerMemo(memo, created, keepPending = false)
            }
            OpRecord.UPSERT_REACTION -> {
                val name = memo?.remoteName ?: return
                val payload = json.decodeFromString(ReactionPayload.serializer(), op.payloadJson)
                val local = memo.reactions.firstOrNull { it.localId == payload.reactionLocalId } ?: return
                if (local.remoteName != null) return
                val created = call {
                    api.upsertMemoReaction(name, UpsertReactionRequestDto(ReactionWriteDto(contentId = name, reactionType = local.reactionType)))
                }
                val fresh = store.memo(memo.localId) ?: return
                store.write { upsertMemo(fresh.copy(reactions = fresh.reactions.map { if (it.localId == local.localId) created.toRecord(local.localId) else it })) }
            }
            OpRecord.DELETE_REACTION -> {
                val payload = json.decodeFromString(ReactionPayload.serializer(), op.payloadJson)
                payload.remoteName?.let { remote ->
                    try {
                        call { api.deleteMemoReaction(remote) }
                    } catch (e: ApiException) {
                        if (!e.isNotFound) throw e
                    }
                }
            }
            OpRecord.SET_RELATIONS -> {
                val name = memo?.remoteName ?: return
                val refs = memo.relations.filter { it.type == "REFERENCE" }.map {
                    MemoRelationWriteDto(memo = MemoRefDto(name), relatedMemo = MemoRefDto(it.relatedRemoteName), type = "REFERENCE")
                }
                call { api.setMemoRelations(name, SetMemoRelationsRequestDto(refs)) }
            }
            OpRecord.SET_LOCATION -> {
                val name = memo?.remoteName ?: return
                val location = memo.latitude?.let { lat -> memo.longitude?.let { lon -> LocationDto(memo.locationPlaceholder.orEmpty(), lat, lon) } }
                val updated = call { api.updateMemo(name, MemoWriteDto(location = location), "location,update_time") }
                absorbServerMemo(memo, updated, keepPending = store.countForMemo(memo.localId) > 1)
            }
        }
    }

    private suspend fun syncAttachmentSet(api: MemosApi, memo: MemoRecord) {
        val name = memo.remoteName ?: return
        val refs = memo.attachments.mapNotNull { it.remoteName }.map { AttachmentRefDto(it) }
        call { api.setMemoAttachments(name, SetMemoAttachmentsRequestDto(refs)) }
    }

    /**
     * Server disagrees and the texts cannot be merged. Keep the server's version under the
     * existing row and park the local text as a new conflict memo so nothing is lost.
     */
    private suspend fun forkConflict(account: AccountRecord, local: MemoRecord, server: MemoDto) {
        val now = Clock.System.now().toEpochMilliseconds()
        store.write {
            val forkId = Uuid.random().toString()
            val fork = local.copy(
                localId = forkId,
                remoteName = null,
                content = local.content.trimEnd() + "\n\n#conflict",
                tags = (local.tags + "conflict").distinct(),
                createTimeEpochMs = now,
                updateTimeEpochMs = now,
                syncStatus = SyncStatus.CONFLICT.name,
                baseUpdateTimeEpochMs = null,
                attachments = emptyList(),
                reactions = emptyList(),
                relations = emptyList(),
            )
            upsertMemo(fork)
            insertOp(OpRecord(0, account.id, forkId, OpRecord.CREATE))
            upsertMemo(server.toRecord(account.id, local).copy(colour = local.colour ?: server.toRecord(account.id, local).colour))
        }
    }

    private suspend fun absorbServerMemo(local: MemoRecord, server: MemoDto, keepPending: Boolean) {
        // Re-read: an earlier op in this run may have changed the row since it was fetched.
        val current = store.memo(local.localId) ?: local
        store.write {
            // CreateMemoComment's response does not echo `parent`; keep what we know locally.
            val record = server.toRecord(current.accountId, current).copy(parent = server.parent ?: current.parent).let {
                if (keepPending) {
                    // Later ops still queued: keep local state so they push the right thing. Unlike
                    // the original this also keeps tags, references and location, which the
                    // queued ops would otherwise push back as the server's.
                    it.copy(
                        content = current.content,
                        tags = current.tags,
                        pinned = current.pinned,
                        visibility = current.visibility,
                        state = current.state,
                        relations = current.relations,
                        latitude = current.latitude,
                        longitude = current.longitude,
                        locationPlaceholder = current.locationPlaceholder,
                        syncStatus = SyncStatus.PENDING_UPDATE.name,
                    )
                } else {
                    it
                }
            }
            upsertMemo(record)
        }
    }

    // ---- pull ------------------------------------------------------------------------

    /**
     * Delta pulls cannot see deletions made elsewhere, so every [RECONCILE_INTERVAL_MS] (or
     * on an explicit full refresh) the whole list is fetched and local rows the server no
     * longer has are dropped. Rows with queued edits are left alone.
     */
    private suspend fun pull(account: AccountRecord, api: MemosApi, full: Boolean) {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        val reconcileDue = full || startedAt - account.lastReconcileEpochMs > RECONCILE_INTERVAL_MS
        val since = account.lastSyncEpochMs?.takeUnless { reconcileDue }
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
                        // Small overlap so clock skew between browser and server cannot drop an edit.
                        filter = since?.let { "updated_ts > timestamp(\"${(it - 60_000).toRfc3339()}\")" },
                    )
                }
                fetched += page.memos
                token = page.nextPageToken.ifEmpty { null }
            } while (token != null)
        }

        store.write {
            for (dto in fetched) {
                val existing = store.memoByRemoteName(account.id, dto.name)
                // A row with queued ops is ahead of the server; leave it for the next push.
                if (existing != null && (existing.syncStatus != SyncStatus.SYNCED.name || store.countForMemo(existing.localId) > 0)) continue
                upsertMemo(dto.toRecord(account.id, existing))
            }
            var reconciledAt = account.lastReconcileEpochMs
            if (since == null) {
                // Comments are not in the list either; they come back when their memo is opened.
                val synced = store.memosFor(account.id).filter { it.syncStatus == SyncStatus.SYNCED.name && it.remoteName != null }
                // An empty result is far more likely to be a server that lost its data, or one
                // answering for the wrong user, than a genuine "you deleted everything".
                if (!(fetched.isEmpty() && synced.isNotEmpty())) {
                    val names = fetched.map { it.name }.toSet()
                    synced.filter { it.remoteName !in names }.forEach { deleteMemo(it.localId) }
                    reconciledAt = startedAt
                }
            }
            val fresh = store.account(account.id) ?: return@write
            updateAccount(fresh.copy(lastSyncEpochMs = startedAt, lastReconcileEpochMs = reconciledAt))
        }
    }

    private suspend fun refreshProfile(account: AccountRecord, api: MemosApi) {
        val user = call { api.getUser(account.userResourceName) }
        val fresh = store.account(account.id) ?: return
        val updated = fresh.copy(displayName = user.displayName.ifEmpty { user.username }, avatarUrl = user.avatarUrl, role = user.role)
        if (updated != fresh) store.write { updateAccount(updated) }
    }

    private suspend fun pullShortcuts(account: AccountRecord, api: MemosApi) {
        val shortcuts = call { api.listShortcuts(account.userResourceName) }.shortcuts
        store.write { putShortcuts(account.id, shortcuts.map { ShortcutRecord(it.name, it.title, it.filter) }) }
    }

    private suspend inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: ResponseException) {
        throw ApiException.from(e, json)
    }

    companion object {
        const val RECONCILE_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }

    @Serializable
    data class UpdatePayload(val baseContent: String? = null)

    @Serializable
    data class DeletePayload(val remoteName: String)

    @Serializable
    data class AttachmentPayload(val attachmentLocalId: String, val remoteName: String? = null)

    @Serializable
    data class ReactionPayload(val reactionLocalId: String, val remoteName: String? = null)
}
