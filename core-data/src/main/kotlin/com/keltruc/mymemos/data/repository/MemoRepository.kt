package com.keltruc.mymemos.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.keltruc.mymemos.data.attachments.AttachmentStore
import com.keltruc.mymemos.data.mapper.toEntity
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.data.mapper.toReference
import com.keltruc.mymemos.data.mapper.relationEntities
import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Location
import com.keltruc.mymemos.model.Reaction
import com.keltruc.mymemos.model.Reference
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.text.TaskListSorter
import com.keltruc.mymemos.data.sync.FailedOp
import com.keltruc.mymemos.data.sync.SyncEngine
import com.keltruc.mymemos.data.sync.SyncScheduler
import com.keltruc.mymemos.data.sync.SyncState
import com.keltruc.mymemos.database.MyMemosDatabase
import com.keltruc.mymemos.database.dao.AttachmentDao
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.database.dao.PendingOpDao
import com.keltruc.mymemos.database.dao.ReactionDao
import com.keltruc.mymemos.database.dao.RelationDao
import com.keltruc.mymemos.database.entity.AttachmentEntity
import com.keltruc.mymemos.database.entity.MemoEntity
import com.keltruc.mymemos.database.entity.MemoRelationEntity
import com.keltruc.mymemos.database.entity.ReactionEntity
import com.keltruc.mymemos.database.entity.PendingOpEntity
import com.keltruc.mymemos.database.entity.PendingOpEntity.Type
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every write lands in Room and the outbox in one transaction, then a sync is scheduled.
 * Reads only ever observe Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MemoRepository @Inject constructor(
    private val db: MyMemosDatabase,
    private val memoDao: MemoDao,
    private val attachmentDao: AttachmentDao,
    private val pendingOpDao: PendingOpDao,
    private val relationDao: RelationDao,
    private val reactionDao: ReactionDao,
    private val registry: ApiClientRegistry,
    private val attachmentStore: AttachmentStore,
    private val engine: SyncEngine,
    private val scheduler: SyncScheduler,
    private val preferences: AppPreferences,
    private val json: Json,
) {
    // ---- reads -----------------------------------------------------------------------

    fun observeTimeline(accountId: Long, state: MemoState = MemoState.NORMAL): Flow<List<Memo>> =
        memoDao.observeTimeline(accountId, state.name).map { rows -> rows.map { it.toModel() } }

    fun observeMemo(localId: String): Flow<Memo?> = memoDao.observeByLocalId(localId).map { it?.toModel() }

    fun search(accountId: Long, query: String): Flow<List<Memo>> {
        val ftsQuery = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString(" ") { "\"${it.replace("\"", "")}\"*" }
        return memoDao.search(accountId, ftsQuery).map { rows -> rows.map { it.toModel() } }
    }

    fun observeTags(accountId: Long): Flow<List<String>> =
        memoDao.observeTagStrings(accountId).map { joined ->
            joined.flatMap { it.split(MemoEntity.TAG_SEPARATOR) }.filter { it.isNotEmpty() }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
        }

    fun observeSyncState(accountId: Long): Flow<SyncState> = combine(
        engine.state,
        pendingOpDao.observeAll(accountId),
        memoDao.observeConflicts(accountId),
    ) { state, ops, conflicts ->
        state.copy(
            pendingCount = ops.count { !it.failed },
            failedCount = ops.count { it.failed },
            conflictCount = conflicts.size,
        )
    }

    fun observeFailedOps(accountId: Long): Flow<List<FailedOp>> =
        pendingOpDao.observeAll(accountId).map { ops ->
            ops.filter { it.failed }.map { FailedOp(it.id, it.memoLocalId, it.type, it.lastError, it.attempts) }
        }

    // ---- sync triggers ---------------------------------------------------------------

    suspend fun syncNow(accountId: Long, full: Boolean = false): SyncEngine.Outcome = engine.sync(accountId, full)

    fun scheduleSync() = scheduler.syncNow()

    suspend fun retryFailed(accountId: Long) {
        pendingOpDao.retryFailed(accountId)
        scheduler.syncNow()
    }

    // ---- writes ----------------------------------------------------------------------

    suspend fun create(accountId: Long, rawContent: String, visibility: Visibility, pinned: Boolean = false): String {
        val content = applyContentRules(rawContent)
        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()
        val entity = MemoEntity(
            localId = localId,
            accountId = accountId,
            remoteName = null,
            creator = null,
            content = content,
            visibility = visibility.name,
            state = MemoState.NORMAL.name,
            pinned = pinned,
            tagsJoined = extractTags(content).joinToString(MemoEntity.TAG_SEPARATOR),
            createTimeEpochMs = now,
            updateTimeEpochMs = now,
            snippet = content.lineSequence().firstOrNull().orEmpty().take(120),
            hasTaskList = content.contains(Regex("^\\s*[-*] \\[[ xX]] ", RegexOption.MULTILINE)),
            hasIncompleteTasks = content.contains(Regex("^\\s*[-*] \\[ ] ", RegexOption.MULTILINE)),
            hasLink = content.contains("http://") || content.contains("https://"),
            hasCode = content.contains("```") || content.contains('`'),
            locationPlaceholder = null,
            latitude = null,
            longitude = null,
            syncStatus = SyncStatus.PENDING_CREATE.name,
            baseUpdateTimeEpochMs = null,
        )
        db.withTransaction {
            memoDao.upsert(entity)
            pendingOpDao.insert(PendingOpEntity(accountId = accountId, memoLocalId = localId, type = Type.CREATE))
        }
        scheduler.syncNow()
        return localId
    }

    suspend fun updateContent(localId: String, rawContent: String) {
        val content = applyContentRules(rawContent)
        db.withTransaction {
            val memo = memoDao.getByLocalId(localId) ?: return@withTransaction
            if (memo.content == content) return@withTransaction
            memoDao.upsert(
                memo.copy(
                    content = content,
                    tagsJoined = extractTags(content).joinToString(MemoEntity.TAG_SEPARATOR),
                    snippet = content.lineSequence().firstOrNull().orEmpty().take(120),
                    hasTaskList = content.contains(Regex("^\\s*[-*] \\[[ xX]] ", RegexOption.MULTILINE)),
                    hasIncompleteTasks = content.contains(Regex("^\\s*[-*] \\[ ] ", RegexOption.MULTILINE)),
                    hasLink = content.contains("http://") || content.contains("https://"),
                    hasCode = content.contains("```") || content.contains('`'),
                    updateTimeEpochMs = System.currentTimeMillis(),
                    syncStatus = if (memo.remoteName == null) memo.syncStatus else SyncStatus.PENDING_UPDATE.name,
                ),
            )
            if (memo.remoteName != null) {
                // Coalesce: one content op per memo, keeping the base from the first unsent edit.
                val prior = pendingOpDao.latestOfType(localId, Type.UPDATE_CONTENT)
                val base = prior?.let { json.decodeFromString<SyncEngine.UpdatePayload>(it.payloadJson).baseContent }
                    ?: memo.content
                pendingOpDao.deleteForMemoOfType(localId, Type.UPDATE_CONTENT)
                pendingOpDao.insert(
                    PendingOpEntity(
                        accountId = memo.accountId,
                        memoLocalId = localId,
                        type = Type.UPDATE_CONTENT,
                        payloadJson = json.encodeToString(SyncEngine.UpdatePayload(base)),
                    ),
                )
            }
        }
        scheduler.syncNow()
    }

    suspend fun setPinned(localId: String, pinned: Boolean) = simpleFieldOp(localId, Type.SET_PINNED) { it.copy(pinned = pinned) }

    suspend fun setVisibility(localId: String, visibility: Visibility) =
        simpleFieldOp(localId, Type.SET_VISIBILITY) { it.copy(visibility = visibility.name) }

    suspend fun setState(localId: String, state: MemoState) =
        simpleFieldOp(localId, Type.SET_STATE) { it.copy(state = state.name) }

    suspend fun delete(localId: String) {
        db.withTransaction {
            val memo = memoDao.getByLocalId(localId) ?: return@withTransaction
            pendingOpDao.deleteForMemo(localId)
            attachmentDao.forMemo(localId).forEach { attachmentStore.delete(it.localId) }
            val remoteName = memo.remoteName
            // The server drops a memo's comments with it; mirror that locally.
            remoteName?.let { memoDao.deleteCommentsOf(memo.accountId, it) }
            if (remoteName == null) {
                memoDao.deleteByLocalId(localId)
            } else {
                memoDao.upsert(memo.copy(syncStatus = SyncStatus.PENDING_DELETE.name))
                pendingOpDao.insert(
                    PendingOpEntity(
                        accountId = memo.accountId,
                        memoLocalId = localId,
                        type = Type.DELETE,
                        payloadJson = json.encodeToString(SyncEngine.DeletePayload(remoteName)),
                    ),
                )
            }
        }
        scheduler.syncNow()
    }

    suspend fun addAttachment(localId: String, uri: Uri) {
        val staged = attachmentStore.stage(uri)
        db.withTransaction {
            val memo = memoDao.getByLocalId(localId) ?: return@withTransaction
            attachmentDao.upsertAll(
                listOf(
                    AttachmentEntity(
                        localId = staged.localId,
                        memoLocalId = localId,
                        remoteName = null,
                        filename = staged.filename,
                        mimeType = staged.mimeType,
                        sizeBytes = staged.size,
                        externalLink = null,
                        localPath = staged.file.absolutePath,
                        createTimeEpochMs = System.currentTimeMillis(),
                    ),
                ),
            )
            pendingOpDao.insert(
                PendingOpEntity(
                    accountId = memo.accountId,
                    memoLocalId = localId,
                    type = Type.ADD_ATTACHMENT,
                    payloadJson = json.encodeToString(
                        SyncEngine.AttachmentPayload.serializer(),
                        SyncEngine.AttachmentPayload(staged.localId),
                    ),
                ),
            )
        }
        scheduler.syncNow()
    }

    suspend fun removeAttachment(attachmentLocalId: String) {
        db.withTransaction {
            val attachment = attachmentDao.byLocalId(attachmentLocalId) ?: return@withTransaction
            val memo = memoDao.getByLocalId(attachment.memoLocalId) ?: return@withTransaction
            attachmentDao.deleteByLocalId(attachmentLocalId)
            if (attachment.remoteName == null) {
                // Never reached the server: drop the queued upload and the file.
                pendingOpDao.deleteForMemoOfType(memo.localId, Type.ADD_ATTACHMENT)
                attachmentStore.delete(attachmentLocalId)
            } else {
                pendingOpDao.insert(
                    PendingOpEntity(
                        accountId = memo.accountId,
                        memoLocalId = memo.localId,
                        type = Type.REMOVE_ATTACHMENT,
                        payloadJson = json.encodeToString(
                            SyncEngine.AttachmentPayload.serializer(),
                            SyncEngine.AttachmentPayload(attachmentLocalId, attachment.remoteName),
                        ),
                    ),
                )
            }
        }
        scheduler.syncNow()
    }

    // ---- comments, reactions, references, location -----------------------------------

    fun observeComments(accountId: Long, parentRemoteName: String): Flow<List<Memo>> =
        memoDao.observeComments(accountId, parentRemoteName).map { rows -> rows.map { it.toModel() } }

    /** Comments are not part of the memo list, so fetch them when a memo is opened. */
    suspend fun refreshComments(account: Account, parentRemoteName: String) {
        val api = registry.api(account.serverUrl, account.userResourceName)
        val comments = api.listMemoComments(parentRemoteName).memos
        db.withTransaction {
            for (dto in comments) {
                val existing = memoDao.getByRemoteName(account.id, dto.name)
                if (existing != null && existing.syncStatus != SyncStatus.SYNCED.name) continue
                memoDao.upsert(dto.toEntity(account.id, existing?.localId).copy(parent = parentRemoteName))
            }
        }
    }

    suspend fun addComment(accountId: Long, parentRemoteName: String, content: String, visibility: Visibility) {
        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()
        db.withTransaction {
            memoDao.upsert(
                MemoEntity(
                    localId = localId, accountId = accountId, remoteName = null, creator = null, content = content,
                    visibility = visibility.name, state = MemoState.NORMAL.name, pinned = false,
                    tagsJoined = extractTags(content).joinToString(MemoEntity.TAG_SEPARATOR),
                    createTimeEpochMs = now, updateTimeEpochMs = now, snippet = content.take(120),
                    hasTaskList = false, hasIncompleteTasks = false, hasLink = false, hasCode = false,
                    locationPlaceholder = null, latitude = null, longitude = null,
                    syncStatus = SyncStatus.PENDING_CREATE.name, baseUpdateTimeEpochMs = null, parent = parentRemoteName,
                ),
            )
            pendingOpDao.insert(PendingOpEntity(accountId = accountId, memoLocalId = localId, type = Type.CREATE_COMMENT))
        }
        scheduler.syncNow()
    }

    fun observeReactions(memoLocalId: String): Flow<List<Reaction>> =
        reactionDao.observeForMemo(memoLocalId).map { list -> list.map { it.toModel() } }

    /** Adds the reaction, or removes it if the current user already reacted with it. */
    suspend fun toggleReaction(memoLocalId: String, userResourceName: String, reactionType: String) {
        db.withTransaction {
            val memo = memoDao.getByLocalId(memoLocalId) ?: return@withTransaction
            val mine = reactionDao.forMemo(memoLocalId).firstOrNull { it.creator == userResourceName && it.reactionType == reactionType }
            if (mine != null) {
                reactionDao.deleteByLocalId(mine.localId)
                pendingOpDao.deleteForMemoOfType(memoLocalId, Type.UPSERT_REACTION)
                if (mine.remoteName != null) {
                    pendingOpDao.insert(
                        PendingOpEntity(
                            accountId = memo.accountId, memoLocalId = memoLocalId, type = Type.DELETE_REACTION,
                            payloadJson = json.encodeToString(SyncEngine.ReactionPayload(mine.localId, mine.remoteName)),
                        ),
                    )
                }
            } else {
                val localId = UUID.randomUUID().toString()
                reactionDao.upsertAll(
                    listOf(ReactionEntity(localId, memoLocalId, null, userResourceName, reactionType, System.currentTimeMillis())),
                )
                pendingOpDao.insert(
                    PendingOpEntity(
                        accountId = memo.accountId, memoLocalId = memoLocalId, type = Type.UPSERT_REACTION,
                        payloadJson = json.encodeToString(SyncEngine.ReactionPayload(localId)),
                    ),
                )
            }
        }
        scheduler.syncNow()
    }

    fun observeReferences(memoLocalId: String): Flow<List<Reference>> =
        relationDao.observeReferences(memoLocalId).map { list -> list.map { it.toReference() } }

    fun observeByRemoteNames(accountId: Long, remoteNames: List<String>): Flow<List<Memo>> =
        memoDao.observeByRemoteNames(accountId, remoteNames).map { rows -> rows.map { it.toModel() } }

    /** Memos that reference [remoteName]. */
    fun observeBacklinks(accountId: Long, remoteName: String): Flow<List<Memo>> =
        relationDao.observeBacklinks(remoteName).flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList()) else memoDao.observeByLocalIds(ids).map { rows -> rows.map { it.toModel() } }
        }

    suspend fun addReference(memoLocalId: String, target: Memo) {
        val targetName = target.remoteName ?: return
        db.withTransaction {
            val memo = memoDao.getByLocalId(memoLocalId) ?: return@withTransaction
            relationDao.upsertAll(listOf(MemoRelationEntity(memoLocalId, targetName, target.snippet.ifEmpty { target.content.take(120) }, "REFERENCE")))
            queueRelations(memo)
        }
        scheduler.syncNow()
    }

    suspend fun removeReference(memoLocalId: String, relatedRemoteName: String) {
        db.withTransaction {
            val memo = memoDao.getByLocalId(memoLocalId) ?: return@withTransaction
            relationDao.deleteReference(memoLocalId, relatedRemoteName)
            queueRelations(memo)
        }
        scheduler.syncNow()
    }

    private suspend fun queueRelations(memo: MemoEntity) {
        if (memo.remoteName == null) return // pushed with the memo once it exists
        pendingOpDao.deleteForMemoOfType(memo.localId, Type.SET_RELATIONS)
        pendingOpDao.insert(PendingOpEntity(accountId = memo.accountId, memoLocalId = memo.localId, type = Type.SET_RELATIONS))
    }

    suspend fun setLocation(memoLocalId: String, location: Location?) = simpleFieldOp(memoLocalId, Type.SET_LOCATION) {
        it.copy(locationPlaceholder = location?.placeholder, latitude = location?.latitude, longitude = location?.longitude)
    }

    /** Candidates for the reference picker: synced, top-level memos matching [query]. */
    suspend fun pickReferenceCandidates(accountId: Long, query: String): List<Memo> =
        memoDao.pickerSearch(accountId, query).map { it.toModel() }.filter { it.remoteName != null }

    /** Local id for a server memo name, pulling it if we do not hold it yet. */
    suspend fun ensureLocal(account: Account, remoteName: String): String? {
        memoDao.getByRemoteName(account.id, remoteName)?.let { return it.localId }
        val dto = runCatching { registry.api(account.serverUrl, account.userResourceName).getMemo(remoteName) }.getOrNull() ?: return null
        val entity = dto.toEntity(account.id)
        memoDao.upsert(entity)
        return entity.localId
    }

    /** Marks a conflict fork as dealt with: it stays as an ordinary memo. */
    suspend fun resolveConflict(localId: String) {
        memoDao.setSyncStatus(localId, SyncStatus.PENDING_CREATE.name)
    }

    /** User-chosen tidy-ups applied to every save, e.g. sinking ticked tasks. */
    private suspend fun applyContentRules(content: String): String =
        if (preferences.current().sortCompletedTasks) TaskListSorter.sortCompletedToBottom(content) else content

    private suspend fun simpleFieldOp(localId: String, type: String, change: (MemoEntity) -> MemoEntity) {
        db.withTransaction {
            val memo = memoDao.getByLocalId(localId) ?: return@withTransaction
            val next = change(memo).copy(
                updateTimeEpochMs = System.currentTimeMillis(),
                syncStatus = if (memo.remoteName == null) memo.syncStatus else SyncStatus.PENDING_UPDATE.name,
            )
            memoDao.upsert(next)
            if (memo.remoteName != null) {
                pendingOpDao.deleteForMemoOfType(localId, type)
                pendingOpDao.insert(PendingOpEntity(accountId = memo.accountId, memoLocalId = localId, type = type))
            }
        }
        scheduler.syncNow()
    }

    companion object {
        private val tagRegex = Regex("(?<![\\w/])#([\\p{L}\\p{N}_/-]+)")

        fun extractTags(content: String): List<String> =
            tagRegex.findAll(content).map { it.groupValues[1].trimEnd('/', '-') }.filter { it.isNotEmpty() }.distinct().toList()
    }
}
