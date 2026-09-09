package com.keltruc.mymemos.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.keltruc.mymemos.data.attachments.AttachmentStore
import com.keltruc.mymemos.data.mapper.toEntity
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.data.mapper.toReference
import com.keltruc.mymemos.data.mapper.relationEntities
import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.data.text.toLocalDateIn
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Location
import com.keltruc.mymemos.model.Reaction
import com.keltruc.mymemos.model.Reference
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.text.TaskListSorter
import com.keltruc.mymemos.data.crypto.MemoCipher
import com.keltruc.mymemos.data.text.ColourTag
import com.keltruc.mymemos.data.widget.WidgetRefresher
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
import kotlin.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import com.keltruc.mymemos.model.NoteColour
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
    private val widgets: WidgetRefresher,
    private val json: Json,
) {
    // ---- reads -----------------------------------------------------------------------

    fun observeTimeline(accountId: Long, byModified: Boolean, state: MemoState = MemoState.NORMAL): Flow<List<Memo>> =
        memoDao.observeTimeline(accountId, byModified, state.name).map { rows -> rows.map { it.toModel() } }

    fun observeMemo(localId: String): Flow<Memo?> = memoDao.observeByLocalId(localId).map { it?.toModel() }

    suspend fun observeMemoOnce(localId: String): Memo? = memoDao.getByLocalId(localId)?.toModel()

    fun search(accountId: Long, query: String): Flow<List<Memo>> {
        val ftsQuery = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString(" ") { "\"${it.replace("\"", "")}\"*" }
        return memoDao.search(accountId, ftsQuery).map { rows -> rows.map { it.toModel() } }
    }

    fun observeTags(accountId: Long): Flow<List<String>> =
        memoDao.observeTagStrings(accountId).map { joined ->
            joined.flatMap { it.split(MemoEntity.TAG_SEPARATOR) }.filter { it.isNotEmpty() && !ColourTag.isColourTag(it) }
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
        widgets.refresh()
    }

    // ---- writes ----------------------------------------------------------------------

    /**
     * [createdAtEpochMs] and [updatedAtEpochMs] exist for import, where a memo's dates come from the
     * file rather than the clock; the sync engine sends the create time on to the server.
     */
    suspend fun create(
        accountId: Long,
        rawContent: String,
        visibility: Visibility,
        pinned: Boolean = false,
        createdAtEpochMs: Long? = null,
        updatedAtEpochMs: Long? = null,
    ): String {
        val content = applyContentRules(rawContent)
        val clock = System.currentTimeMillis()
        val created = createdAtEpochMs ?: clock
        val updated = updatedAtEpochMs ?: created
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
            createTimeEpochMs = created,
            updateTimeEpochMs = updated,
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
        widgets.refresh()
        return localId
    }

    suspend fun updateContent(localId: String, rawContent: String) {
        db.withTransaction {
            val memo = memoDao.getByLocalId(localId) ?: return@withTransaction
            var content = applyContentRules(rawContent)
            // Editors work on the text without the colour line; put it back so the tint survives.
            val keptColour = memo.colour?.let { c -> runCatching { NoteColour.valueOf(c) }.getOrNull() }
            if (!MemoCipher.isEncrypted(content) && ColourTag.extract(content) == null && keptColour != null) {
                content = ColourTag.apply(content, keptColour)
            }
            if (memo.content == content) return@withTransaction
            memoDao.upsert(
                memo.copy(
                    content = content,
                    colour = if (MemoCipher.isEncrypted(content)) memo.colour else ColourTag.extract(content)?.name,
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
        widgets.refresh()
    }

    suspend fun setPinned(localId: String, pinned: Boolean) = simpleFieldOp(localId, Type.SET_PINNED) { it.copy(pinned = pinned) }

    suspend fun setVisibility(localId: String, visibility: Visibility) =
        simpleFieldOp(localId, Type.SET_VISIBILITY) { it.copy(visibility = visibility.name) }

    suspend fun setState(localId: String, state: MemoState) =
        simpleFieldOp(localId, Type.SET_STATE) { it.copy(state = state.name) }

    /**
     * Returns whether the delete can still be taken back. A memo that reached the server is only
     * marked for deletion until the outbox flushes, so it can; one that never synced goes for good.
     *
     * Attachment files deliberately stay on disk for a memo that is only marked: the sync engine
     * removes them once the server has confirmed the delete. Removing them here would leave undo
     * restoring a memo with its images missing.
     */
    suspend fun delete(localId: String): Boolean {
        var undoable = false
        db.withTransaction {
            val memo = memoDao.getByLocalId(localId) ?: return@withTransaction
            pendingOpDao.deleteForMemo(localId)
            val remoteName = memo.remoteName
            // The server drops a memo's comments with it; mirror that locally.
            remoteName?.let { memoDao.deleteCommentsOf(memo.accountId, it) }
            if (remoteName == null) {
                attachmentDao.forMemo(localId).forEach { attachmentStore.delete(it.localId) }
                memoDao.deleteByLocalId(localId)
            } else {
                undoable = true
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
        // Held back so the snackbar's Undo has something left to undo.
        if (undoable) scheduler.syncNow(afterMs = UNDO_WINDOW_MS) else scheduler.syncNow()
        widgets.refresh()
        return undoable
    }

    /**
     * Takes back a [delete] that has not reached the server yet, returning false when the outbox
     * already flushed it. Any unsent edits made before the delete are not restored: [delete] clears
     * the memo's queued operations, so what comes back is the memo as the server last had it.
     */
    /** The memo this account already holds under a server name, if any. Used to spot re-imports. */
    suspend fun findByRemoteName(accountId: Long, remoteName: String): Memo? =
        memoDao.getByRemoteName(accountId, remoteName)?.toModel()

    suspend fun undoDelete(localId: String): Boolean {
        var restored = false
        db.withTransaction {
            val memo = memoDao.getByLocalId(localId) ?: return@withTransaction
            if (memo.syncStatus != SyncStatus.PENDING_DELETE.name) return@withTransaction
            if (pendingOpDao.latestOfType(localId, Type.DELETE) == null) return@withTransaction
            pendingOpDao.deleteForMemoOfType(localId, Type.DELETE)
            memoDao.upsert(memo.copy(syncStatus = SyncStatus.SYNCED.name))
            restored = true
        }
        if (restored) {
            scheduler.syncNow()
            widgets.refresh()
        }
        return restored
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
        widgets.refresh()
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
        widgets.refresh()
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
                memoDao.upsert(dto.toEntity(account.id, existing?.localId, existing?.colour).copy(parent = parentRemoteName))
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
        widgets.refresh()
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
        widgets.refresh()
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
        widgets.refresh()
    }

    suspend fun removeReference(memoLocalId: String, relatedRemoteName: String) {
        db.withTransaction {
            val memo = memoDao.getByLocalId(memoLocalId) ?: return@withTransaction
            relationDao.deleteReference(memoLocalId, relatedRemoteName)
            queueRelations(memo)
        }
        scheduler.syncNow()
        widgets.refresh()
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

    // ---- end-to-end encryption ---------------------------------------------------------

    /** Replaces the memo text with its encrypted form. The server only ever sees the blob. */
    suspend fun lock(localId: String, password: CharArray) {
        val memo = memoDao.getByLocalId(localId) ?: return
        if (MemoCipher.isEncrypted(memo.content)) return
        updateContent(localId, MemoCipher.encrypt(memo.content, password))
    }

    /** Stores the plain text again. Throws [MemoCipher.WrongPassword] if the password is wrong. */
    suspend fun unlock(localId: String, password: CharArray) {
        val memo = memoDao.getByLocalId(localId) ?: return
        if (!MemoCipher.isEncrypted(memo.content)) return
        updateContent(localId, MemoCipher.decrypt(memo.content, password))
    }

    /** Edits a locked memo: the new plain text is encrypted before it is stored. */
    suspend fun updateLockedContent(localId: String, plain: String, password: CharArray) =
        updateContent(localId, MemoCipher.encrypt(plain, password))

    fun decrypt(memo: Memo, password: CharArray): String = MemoCipher.decrypt(memo.content, password)

    /**
     * Sets the tint. For plain memos it rides along as a `#colour/x` tag line so other
     * devices pick it up; locked memos keep it on this device only, since nothing readable
     * can sit next to the ciphertext.
     */
    suspend fun setColour(localId: String, colour: NoteColour?) {
        val memo = memoDao.getByLocalId(localId) ?: return
        memoDao.setColour(localId, colour?.name)
        if (!MemoCipher.isEncrypted(memo.content)) {
            updateContent(localId, ColourTag.apply(memo.content, colour))
        }
        widgets.refresh()
    }

    fun observeCreatedOn(accountId: Long, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<Memo>> {
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return memoDao.observeCreatedBetween(accountId, from, to).map { rows -> rows.map { it.toModel() } }
    }

    /** Days with at least one memo, for streaks and "on this day". */
    fun observeActiveDays(accountId: Long, zone: ZoneId = ZoneId.systemDefault()): Flow<Set<LocalDate>> =
        memoDao.observeCreateTimes(accountId).map { times -> times.map { it.toLocalDateIn(zone) }.toSet() }

    suspend fun memosWithLocation(accountId: Long): List<Memo> = memoDao.withLocation(accountId).map { it.toModel() }

    /** Every top-level memo, for the weekly digest. */
    suspend fun allForDigest(accountId: Long): List<Memo> =
        memoDao.allForExport(accountId).map { it.toModel() }.filter { !it.isComment && !ColourTag.isColourTag("") && !it.tags.contains("mymemos/config") && !it.content.contains("#mymemos/config") }

    /** Nodes and reference edges for the graph view. */
    suspend fun referenceGraph(accountId: Long): Pair<List<Memo>, List<Pair<String, String>>> {
        val memos = memoDao.allForExport(accountId).map { it.toModel() }.filter { it.remoteName != null && !it.isComment }
        val byLocal = memos.associateBy { it.localId }
        val byRemote = memos.associateBy { it.remoteName }
        val edges = memoDao.allReferences().mapNotNull { r ->
            val from = byLocal[r.memoLocalId] ?: return@mapNotNull null
            val to = byRemote[r.relatedRemoteName] ?: return@mapNotNull null
            from.localId to to.localId
        }
        val connected = edges.flatMap { listOf(it.first, it.second) }.toSet()
        return memos.filter { it.localId in connected } to edges
    }

    /** Memos with unticked tasks as a live flow, for the tasks screen. */
    fun observeMemosWithOpenTasks(accountId: Long): Flow<List<Memo>> =
        memoDao.observeWithOpenTasks(accountId).map { rows -> rows.map { it.toModel() } }

    /** Memos with unticked tasks, newest first, for the tasks widget. */
    suspend fun memosWithOpenTasks(accountId: Long): List<Memo> =
        memoDao.withOpenTasks(accountId).map { it.toModel() }

    /** Pinned then most recent memos, for the recents widget. */
    suspend fun recentForWidget(accountId: Long, limit: Int): List<Memo> =
        memoDao.recent(accountId, limit).map { it.toModel() }

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
        if (!MemoCipher.isEncrypted(content) && preferences.current().sortCompletedTasks) TaskListSorter.sortCompletedToBottom(content) else content

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
        widgets.refresh()
    }

    companion object {
        /** How long a delete waits before it is sent, which is how long Undo has to work. */
        const val UNDO_WINDOW_MS = 5_000L

        private val tagRegex = Regex("(?<![\\w/])#([\\p{L}\\p{N}_/-]+)")

        fun extractTags(content: String): List<String> =
            tagRegex.findAll(content).map { it.groupValues[1].trimEnd('/', '-') }
                .filter { it.isNotEmpty() && !ColourTag.isColourTag(it) }.distinct().toList()
    }
}
