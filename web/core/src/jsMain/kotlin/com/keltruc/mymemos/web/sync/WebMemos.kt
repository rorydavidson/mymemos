package com.keltruc.mymemos.web.sync

import com.keltruc.mymemos.data.crypto.MemoCipher
import com.keltruc.mymemos.data.text.ColourTag
import com.keltruc.mymemos.data.text.TaskListSorter
import com.keltruc.mymemos.model.Location
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.NoteColour
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.web.store.AttachmentRecord
import com.keltruc.mymemos.web.store.MemoRecord
import com.keltruc.mymemos.web.store.OpRecord
import com.keltruc.mymemos.web.store.ReactionRecord
import com.keltruc.mymemos.web.store.RelationRecord
import com.keltruc.mymemos.web.store.WebStore
import com.keltruc.mymemos.web.store.withContent
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Every write lands in the store and the outbox together, then a sync is asked for: the web
 * port of core-data's MemoRepository writes. Reads are WebSession's, straight off the store.
 */
class WebMemos(
    private val store: WebStore,
    private val scheduler: SyncScheduler,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    /** [createdAtEpochMs] and [updatedAtEpochMs] exist for import, where dates come from the file. */
    suspend fun create(
        accountId: Int,
        rawContent: String,
        visibility: Visibility,
        pinned: Boolean = false,
        createdAtEpochMs: Long? = null,
        updatedAtEpochMs: Long? = null,
    ): String {
        val content = applyContentRules(rawContent)
        val created = createdAtEpochMs ?: now()
        val localId = Uuid.random().toString()
        val record = MemoRecord(
            localId = localId, accountId = accountId, remoteName = null, creator = null, content = "",
            visibility = visibility.name, state = MemoState.NORMAL.name, pinned = pinned, tags = emptyList(),
            createTimeEpochMs = created, updateTimeEpochMs = updatedAtEpochMs ?: created, snippet = "",
            hasTaskList = false, hasIncompleteTasks = false, hasLink = false, hasCode = false,
            syncStatus = SyncStatus.PENDING_CREATE.name, baseUpdateTimeEpochMs = null,
        ).withContent(content)
        store.write {
            upsertMemo(record)
            insertOp(OpRecord(0, accountId, localId, OpRecord.CREATE))
        }
        scheduler.syncNow()
        return localId
    }

    suspend fun updateContent(localId: String, rawContent: String) {
        val changed = store.write {
            val memo = store.memo(localId) ?: return@write false
            var content = applyContentRules(rawContent)
            // Editors work on the text without the colour line; put it back so the tint survives.
            val keptColour = memo.colour?.let { c -> runCatching { NoteColour.valueOf(c) }.getOrNull() }
            if (!MemoCipher.isEncrypted(content) && ColourTag.extract(content) == null && keptColour != null) {
                content = ColourTag.apply(content, keptColour)
            }
            if (memo.content == content) return@write false
            upsertMemo(
                memo.withContent(content).copy(
                    colour = if (MemoCipher.isEncrypted(content)) memo.colour else ColourTag.extract(content)?.name,
                    updateTimeEpochMs = now(),
                    syncStatus = if (memo.remoteName == null) memo.syncStatus else SyncStatus.PENDING_UPDATE.name,
                ),
            )
            if (memo.remoteName != null) {
                // Coalesce: one content op per memo, keeping the base from the first unsent edit.
                val prior = store.latestOfType(localId, OpRecord.UPDATE_CONTENT)
                val base = prior?.let { json.decodeFromString(WebSyncEngine.UpdatePayload.serializer(), it.payloadJson).baseContent }
                    ?: memo.content
                deleteOpsForMemo(localId, OpRecord.UPDATE_CONTENT)
                insertOp(
                    OpRecord(0, memo.accountId, localId, OpRecord.UPDATE_CONTENT,
                        json.encodeToString(WebSyncEngine.UpdatePayload.serializer(), WebSyncEngine.UpdatePayload(base))),
                )
            }
            true
        }
        if (changed) scheduler.syncNow()
    }

    suspend fun setPinned(localId: String, pinned: Boolean) = simpleFieldOp(localId, OpRecord.SET_PINNED) { it.copy(pinned = pinned) }

    suspend fun setVisibility(localId: String, visibility: Visibility) =
        simpleFieldOp(localId, OpRecord.SET_VISIBILITY) { it.copy(visibility = visibility.name) }

    suspend fun setState(localId: String, state: MemoState) = simpleFieldOp(localId, OpRecord.SET_STATE) { it.copy(state = state.name) }

    suspend fun setLocation(localId: String, location: Location?) = simpleFieldOp(localId, OpRecord.SET_LOCATION) {
        it.copy(locationPlaceholder = location?.placeholder, latitude = location?.latitude, longitude = location?.longitude)
    }

    /**
     * Returns whether the delete can still be taken back. A memo that reached the server is only
     * marked until the outbox flushes, which is held back for [UNDO_WINDOW_MS]; one that never
     * synced goes for good.
     */
    suspend fun delete(localId: String): Boolean {
        val memo = store.memo(localId) ?: return false
        val remoteName = memo.remoteName
        store.write {
            deleteOpsForMemo(localId)
            // The server drops a memo's comments with it; mirror that locally.
            remoteName?.let { parent -> store.memosFor(memo.accountId).filter { it.parent == parent }.forEach { deleteMemo(it.localId) } }
            if (remoteName == null) {
                deleteMemo(localId)
            } else {
                upsertMemo(memo.copy(syncStatus = SyncStatus.PENDING_DELETE.name))
                insertOp(OpRecord(0, memo.accountId, localId, OpRecord.DELETE, json.encodeToString(WebSyncEngine.DeletePayload.serializer(), WebSyncEngine.DeletePayload(remoteName))))
            }
        }
        if (remoteName == null) {
            memo.attachments.forEach { store.deleteBlob(it.localId) }
            scheduler.syncNow()
            return false
        }
        // Held back so Undo has something left to undo.
        scheduler.syncNow(afterMs = UNDO_WINDOW_MS)
        return true
    }

    /** Takes back a [delete] that has not reached the server yet; false once it has. */
    suspend fun undoDelete(localId: String): Boolean {
        val restored = store.write {
            val memo = store.memo(localId) ?: return@write false
            if (memo.syncStatus != SyncStatus.PENDING_DELETE.name) return@write false
            if (store.latestOfType(localId, OpRecord.DELETE) == null) return@write false
            deleteOpsForMemo(localId, OpRecord.DELETE)
            upsertMemo(memo.copy(syncStatus = SyncStatus.SYNCED.name))
            true
        }
        if (restored) scheduler.syncNow()
        return restored
    }

    /** Records bytes the page has already read from a file the user picked. */
    suspend fun addAttachment(localId: String, filename: String, mimeType: String, bytes: ByteArray) {
        val attachmentId = Uuid.random().toString()
        store.writeBlob(attachmentId, bytes)
        store.write {
            val memo = store.memo(localId) ?: return@write
            val record = AttachmentRecord(attachmentId, null, filename, mimeType, bytes.size.toLong(), null, now())
            upsertMemo(memo.copy(attachments = memo.attachments + record))
            insertOp(OpRecord(0, memo.accountId, localId, OpRecord.ADD_ATTACHMENT,
                json.encodeToString(WebSyncEngine.AttachmentPayload.serializer(), WebSyncEngine.AttachmentPayload(attachmentId))))
        }
        scheduler.syncNow()
    }

    suspend fun removeAttachment(memoLocalId: String, attachmentLocalId: String) {
        val memo = store.memo(memoLocalId) ?: return
        val attachment = memo.attachments.firstOrNull { it.localId == attachmentLocalId } ?: return
        store.write {
            upsertMemo(memo.copy(attachments = memo.attachments.filter { it.localId != attachmentLocalId }))
            if (attachment.remoteName == null) {
                // Never reached the server: drop the queued upload.
                store.opsForMemo(memoLocalId).filter { it.type == OpRecord.ADD_ATTACHMENT && it.payloadJson.contains(attachmentLocalId) }.forEach { deleteOp(it.id) }
            } else {
                insertOp(OpRecord(0, memo.accountId, memoLocalId, OpRecord.REMOVE_ATTACHMENT,
                    json.encodeToString(WebSyncEngine.AttachmentPayload.serializer(), WebSyncEngine.AttachmentPayload(attachmentLocalId, attachment.remoteName))))
            }
        }
        if (attachment.remoteName == null) store.deleteBlob(attachmentLocalId)
        scheduler.syncNow()
    }

    suspend fun addComment(accountId: Int, parentRemoteName: String, content: String, visibility: Visibility) {
        val localId = Uuid.random().toString()
        val t = now()
        store.write {
            upsertMemo(
                MemoRecord(
                    localId = localId, accountId = accountId, remoteName = null, creator = null, content = "",
                    visibility = visibility.name, state = MemoState.NORMAL.name, pinned = false, tags = emptyList(),
                    createTimeEpochMs = t, updateTimeEpochMs = t, snippet = "",
                    hasTaskList = false, hasIncompleteTasks = false, hasLink = false, hasCode = false,
                    syncStatus = SyncStatus.PENDING_CREATE.name, baseUpdateTimeEpochMs = null, parent = parentRemoteName,
                ).withContent(content).copy(hasTaskList = false, hasIncompleteTasks = false, hasLink = false, hasCode = false),
            )
            insertOp(OpRecord(0, accountId, localId, OpRecord.CREATE_COMMENT))
        }
        scheduler.syncNow()
    }

    /** Adds the reaction, or removes it if the current user already reacted with it. */
    suspend fun toggleReaction(memoLocalId: String, userResourceName: String, reactionType: String) {
        store.write {
            val memo = store.memo(memoLocalId) ?: return@write
            val mine = memo.reactions.firstOrNull { it.creator == userResourceName && it.reactionType == reactionType }
            if (mine != null) {
                upsertMemo(memo.copy(reactions = memo.reactions.filter { it.localId != mine.localId }))
                deleteOpsForMemo(memoLocalId, OpRecord.UPSERT_REACTION)
                if (mine.remoteName != null) {
                    insertOp(OpRecord(0, memo.accountId, memoLocalId, OpRecord.DELETE_REACTION,
                        json.encodeToString(WebSyncEngine.ReactionPayload.serializer(), WebSyncEngine.ReactionPayload(mine.localId, mine.remoteName))))
                }
            } else {
                val localId = Uuid.random().toString()
                upsertMemo(memo.copy(reactions = memo.reactions + ReactionRecord(localId, null, userResourceName, reactionType, now())))
                insertOp(OpRecord(0, memo.accountId, memoLocalId, OpRecord.UPSERT_REACTION,
                    json.encodeToString(WebSyncEngine.ReactionPayload.serializer(), WebSyncEngine.ReactionPayload(localId))))
            }
        }
        scheduler.syncNow()
    }

    suspend fun addReference(memoLocalId: String, target: MemoRecord) {
        val targetName = target.remoteName ?: return
        store.write {
            val memo = store.memo(memoLocalId) ?: return@write
            val ref = RelationRecord(targetName, target.snippet.ifEmpty { target.content.take(120) })
            upsertMemo(memo.copy(relations = memo.relations.filter { it.relatedRemoteName != targetName } + ref))
            queueRelations(memo)
        }
        scheduler.syncNow()
    }

    suspend fun removeReference(memoLocalId: String, relatedRemoteName: String) {
        store.write {
            val memo = store.memo(memoLocalId) ?: return@write
            upsertMemo(memo.copy(relations = memo.relations.filter { it.relatedRemoteName != relatedRemoteName }))
            queueRelations(memo)
        }
        scheduler.syncNow()
    }

    private fun WebStore.Writer.queueRelations(memo: MemoRecord) {
        if (memo.remoteName == null) return // pushed with the memo once it exists
        deleteOpsForMemo(memo.localId, OpRecord.SET_RELATIONS)
        insertOp(OpRecord(0, memo.accountId, memo.localId, OpRecord.SET_RELATIONS))
    }

    /**
     * Sets the tint. For plain memos it rides along as a `#colour/x` line so other devices
     * pick it up; locked memos keep it in this browser only.
     */
    suspend fun setColour(localId: String, colour: NoteColour?) {
        val memo = store.memo(localId) ?: return
        store.write { upsertMemo(memo.copy(colour = colour?.name)) }
        if (!MemoCipher.isEncrypted(memo.content)) updateContent(localId, ColourTag.apply(memo.content, colour))
    }

    /** Marks a conflict fork as dealt with: it stays as an ordinary memo. */
    suspend fun resolveConflict(localId: String) {
        val memo = store.memo(localId) ?: return
        store.write { upsertMemo(memo.copy(syncStatus = SyncStatus.PENDING_CREATE.name)) }
    }

    suspend fun retryFailed(accountId: Int) {
        store.write { retryFailed(accountId) }
        scheduler.syncNow()
    }

    private fun applyContentRules(content: String): String =
        if (!MemoCipher.isEncrypted(content) && store.prefs.sortCompletedTasks) TaskListSorter.sortCompletedToBottom(content) else content

    private suspend fun simpleFieldOp(localId: String, type: String, change: (MemoRecord) -> MemoRecord) {
        store.write {
            val memo = store.memo(localId) ?: return@write
            upsertMemo(
                change(memo).copy(
                    updateTimeEpochMs = now(),
                    syncStatus = if (memo.remoteName == null) memo.syncStatus else SyncStatus.PENDING_UPDATE.name,
                ),
            )
            if (memo.remoteName != null) {
                deleteOpsForMemo(localId, type)
                insertOp(OpRecord(0, memo.accountId, localId, type))
            }
        }
        scheduler.syncNow()
    }

    companion object {
        /** How long a delete waits before it is sent, which is how long Undo has to work. */
        const val UNDO_WINDOW_MS = 5_000L
    }
}
