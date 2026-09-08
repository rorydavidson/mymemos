package com.keltruc.mymemos.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.keltruc.mymemos.database.entity.AttachmentEntity
import com.keltruc.mymemos.database.entity.MemoEntity
import com.keltruc.mymemos.database.entity.MemoRelationEntity
import kotlinx.coroutines.flow.Flow

data class MemoWithAttachments(
    @Embedded val memo: MemoEntity,
    @Relation(parentColumn = "localId", entityColumn = "memoLocalId")
    val attachments: List<AttachmentEntity>,
)

@Dao
interface MemoDao {

    @Transaction
    @Query(
        """
        SELECT * FROM memos
        WHERE accountId = :accountId AND state = :state AND syncStatus != 'PENDING_DELETE' AND parent IS NULL AND tagsJoined NOT LIKE '%mymemos/config%'
        ORDER BY pinned DESC, createTimeEpochMs DESC
        """,
    )
    fun observeTimeline(accountId: Long, state: String = "NORMAL"): Flow<List<MemoWithAttachments>>

    @Transaction
    @Query(
        """
        SELECT memos.* FROM memos
        JOIN memos_fts ON memos.rowid = memos_fts.rowid
        WHERE memos.accountId = :accountId AND memos.syncStatus != 'PENDING_DELETE' AND memos.parent IS NULL
          AND memos.tagsJoined NOT LIKE '%mymemos/config%' AND memos_fts MATCH :query
        ORDER BY memos.createTimeEpochMs DESC
        """,
    )
    fun search(accountId: Long, query: String): Flow<List<MemoWithAttachments>>

    @Transaction
    @Query("SELECT * FROM memos WHERE localId = :localId")
    fun observeByLocalId(localId: String): Flow<MemoWithAttachments?>

    @Query("SELECT * FROM memos WHERE localId = :localId")
    suspend fun getByLocalId(localId: String): MemoEntity?

    @Query("SELECT * FROM memos WHERE accountId = :accountId AND remoteName = :remoteName")
    suspend fun getByRemoteName(accountId: Long, remoteName: String): MemoEntity?

    @Query("SELECT * FROM memos WHERE accountId = :accountId AND syncStatus != 'SYNCED' ORDER BY updateTimeEpochMs")
    suspend fun pendingForAccount(accountId: Long): List<MemoEntity>

    @Query("SELECT tagsJoined FROM memos WHERE accountId = :accountId AND tagsJoined != '' AND tagsJoined NOT LIKE '%mymemos/config%'")
    fun observeTagStrings(accountId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(memos: List<MemoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memo: MemoEntity)

    @Update
    suspend fun update(memo: MemoEntity)

    @Query("DELETE FROM memos WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Transaction
    @Query("SELECT * FROM memos WHERE accountId = :accountId AND syncStatus != 'PENDING_DELETE' ORDER BY createTimeEpochMs")
    suspend fun allForExport(accountId: Long): List<MemoWithAttachments>

    @Query("SELECT * FROM memo_relations WHERE type = 'REFERENCE'")
    suspend fun allReferences(): List<MemoRelationEntity>

    @Query("UPDATE memos SET colour = :colour WHERE localId = :localId")
    suspend fun setColour(localId: String, colour: String?)

    @Transaction
    @Query("SELECT * FROM memos WHERE accountId = :accountId AND parent IS NULL AND syncStatus != 'PENDING_DELETE' AND tagsJoined NOT LIKE '%mymemos/config%' AND createTimeEpochMs >= :fromEpochMs AND createTimeEpochMs < :toEpochMs ORDER BY createTimeEpochMs")
    fun observeCreatedBetween(accountId: Long, fromEpochMs: Long, toEpochMs: Long): Flow<List<MemoWithAttachments>>

    @Query("SELECT createTimeEpochMs FROM memos WHERE accountId = :accountId AND parent IS NULL AND syncStatus != 'PENDING_DELETE' AND tagsJoined NOT LIKE '%mymemos/config%'")
    fun observeCreateTimes(accountId: Long): Flow<List<Long>>

    @Transaction
    @Query("SELECT * FROM memos WHERE accountId = :accountId AND parent IS NULL AND syncStatus != 'PENDING_DELETE' AND latitude IS NOT NULL ORDER BY createTimeEpochMs DESC")
    suspend fun withLocation(accountId: Long): List<MemoWithAttachments>

    @Query("UPDATE memos SET syncStatus = :status WHERE localId = :localId")
    suspend fun setSyncStatus(localId: String, status: String)

    @Query("SELECT * FROM memos WHERE accountId = :accountId AND syncStatus = 'CONFLICT'")
    fun observeConflicts(accountId: Long): Flow<List<MemoEntity>>

    @Transaction
    @Query("SELECT * FROM memos WHERE accountId = :accountId AND parent = :parentRemoteName AND syncStatus != 'PENDING_DELETE' ORDER BY createTimeEpochMs")
    fun observeComments(accountId: Long, parentRemoteName: String): Flow<List<MemoWithAttachments>>

    @Transaction
    @Query("SELECT * FROM memos WHERE accountId = :accountId AND remoteName IN (:remoteNames) AND syncStatus != 'PENDING_DELETE' ORDER BY pinned DESC, createTimeEpochMs DESC")
    fun observeByRemoteNames(accountId: Long, remoteNames: List<String>): Flow<List<MemoWithAttachments>>

    @Transaction
    @Query("SELECT * FROM memos WHERE localId IN (:localIds) AND syncStatus != 'PENDING_DELETE' ORDER BY createTimeEpochMs DESC")
    fun observeByLocalIds(localIds: List<String>): Flow<List<MemoWithAttachments>>

    @Transaction
    @Query(
        """
        SELECT * FROM memos WHERE accountId = :accountId AND parent IS NULL AND syncStatus != 'PENDING_DELETE'
          AND (content LIKE '%' || :query || '%') ORDER BY updateTimeEpochMs DESC LIMIT 30
        """,
    )
    suspend fun pickerSearch(accountId: Long, query: String): List<MemoWithAttachments>

    @Query(
        """
        DELETE FROM memos WHERE accountId = :accountId AND syncStatus = 'SYNCED'
          AND remoteName IS NOT NULL AND remoteName NOT IN (:keepRemoteNames)
        """,
    )
    suspend fun deleteSyncedNotIn(accountId: Long, keepRemoteNames: List<String>)

    @Transaction
    @Query("SELECT * FROM memos WHERE accountId = :accountId AND state = 'NORMAL' AND parent IS NULL AND hasIncompleteTasks = 1 AND syncStatus != 'PENDING_DELETE' ORDER BY pinned DESC, updateTimeEpochMs DESC LIMIT 20")
    suspend fun withOpenTasks(accountId: Long): List<MemoWithAttachments>

    @Transaction
    @Query("SELECT * FROM memos WHERE accountId = :accountId AND state = 'NORMAL' AND parent IS NULL AND hasIncompleteTasks = 1 AND syncStatus != 'PENDING_DELETE' ORDER BY pinned DESC, updateTimeEpochMs DESC")
    fun observeWithOpenTasks(accountId: Long): Flow<List<MemoWithAttachments>>

    /** Creator is NULL until the memo has been pushed, so locally created config counts as ours. */
    @Query("SELECT * FROM memos WHERE accountId = :accountId AND tagsJoined LIKE '%mymemos/config%' AND (creator IS NULL OR creator = :creator) AND syncStatus != 'PENDING_DELETE' ORDER BY createTimeEpochMs LIMIT 1")
    fun observeConfigMemo(accountId: Long, creator: String): Flow<MemoEntity?>

    @Transaction
    @Query("SELECT * FROM memos WHERE accountId = :accountId AND state = 'NORMAL' AND parent IS NULL AND syncStatus != 'PENDING_DELETE' AND tagsJoined NOT LIKE '%mymemos/config%' ORDER BY pinned DESC, createTimeEpochMs DESC LIMIT :limit")
    suspend fun recent(accountId: Long, limit: Int): List<MemoWithAttachments>

    @Query("DELETE FROM memos WHERE accountId = :accountId AND parent = :parentRemoteName")
    suspend fun deleteCommentsOf(accountId: Long, parentRemoteName: String)

    @Query("DELETE FROM memos WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: Long)
}
