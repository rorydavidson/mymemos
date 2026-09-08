package com.keltruc.mymemos.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.keltruc.mymemos.database.entity.PendingOpEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOpDao {
    @Insert
    suspend fun insert(op: PendingOpEntity): Long

    @Update
    suspend fun update(op: PendingOpEntity)

    @Query("SELECT * FROM pending_ops WHERE accountId = :accountId AND failed = 0 ORDER BY id")
    suspend fun queued(accountId: Long): List<PendingOpEntity>

    @Query("SELECT * FROM pending_ops WHERE accountId = :accountId ORDER BY id")
    fun observeAll(accountId: Long): Flow<List<PendingOpEntity>>

    @Query("SELECT COUNT(*) FROM pending_ops WHERE accountId = :accountId AND failed = 0")
    fun observeQueuedCount(accountId: Long): Flow<Int>

    @Query("SELECT * FROM pending_ops WHERE memoLocalId = :memoLocalId AND type = :type AND failed = 0 ORDER BY id DESC LIMIT 1")
    suspend fun latestOfType(memoLocalId: String, type: String): PendingOpEntity?

    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_ops WHERE memoLocalId = :memoLocalId")
    suspend fun deleteForMemo(memoLocalId: String)

    @Query("DELETE FROM pending_ops WHERE memoLocalId = :memoLocalId AND type = :type")
    suspend fun deleteForMemoOfType(memoLocalId: String, type: String)

    @Query("UPDATE pending_ops SET failed = 0, attempts = 0, lastError = NULL WHERE accountId = :accountId AND failed = 1")
    suspend fun retryFailed(accountId: Long)

    @Query("SELECT COUNT(*) FROM pending_ops WHERE memoLocalId = :memoLocalId AND failed = 0")
    suspend fun countForMemo(memoLocalId: String): Int
}
