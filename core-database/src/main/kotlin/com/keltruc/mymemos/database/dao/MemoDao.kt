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
        WHERE accountId = :accountId AND state = :state AND syncStatus != 'PENDING_DELETE'
        ORDER BY pinned DESC, createTimeEpochMs DESC
        """,
    )
    fun observeTimeline(accountId: Long, state: String = "NORMAL"): Flow<List<MemoWithAttachments>>

    @Transaction
    @Query(
        """
        SELECT memos.* FROM memos
        JOIN memos_fts ON memos.rowid = memos_fts.rowid
        WHERE memos.accountId = :accountId AND memos.syncStatus != 'PENDING_DELETE'
          AND memos_fts MATCH :query
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

    @Query("SELECT tagsJoined FROM memos WHERE accountId = :accountId AND tagsJoined != ''")
    fun observeTagStrings(accountId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(memos: List<MemoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memo: MemoEntity)

    @Update
    suspend fun update(memo: MemoEntity)

    @Query("DELETE FROM memos WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("UPDATE memos SET syncStatus = :status WHERE localId = :localId")
    suspend fun setSyncStatus(localId: String, status: String)

    @Query("SELECT * FROM memos WHERE accountId = :accountId AND syncStatus = 'CONFLICT'")
    fun observeConflicts(accountId: Long): Flow<List<MemoEntity>>

    @Query(
        """
        DELETE FROM memos WHERE accountId = :accountId AND syncStatus = 'SYNCED'
          AND remoteName IS NOT NULL AND remoteName NOT IN (:keepRemoteNames)
        """,
    )
    suspend fun deleteSyncedNotIn(accountId: Long, keepRemoteNames: List<String>)

    @Query("DELETE FROM memos WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: Long)
}
