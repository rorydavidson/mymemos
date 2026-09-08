package com.keltruc.mymemos.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.keltruc.mymemos.database.entity.ReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReactionDao {
    @Query("SELECT * FROM reactions WHERE memoLocalId = :memoLocalId ORDER BY createTimeEpochMs")
    fun observeForMemo(memoLocalId: String): Flow<List<ReactionEntity>>

    @Query("SELECT * FROM reactions WHERE memoLocalId = :memoLocalId")
    suspend fun forMemo(memoLocalId: String): List<ReactionEntity>

    @Query("SELECT * FROM reactions WHERE localId = :localId")
    suspend fun byLocalId(localId: String): ReactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reactions: List<ReactionEntity>)

    @Query("DELETE FROM reactions WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("DELETE FROM reactions WHERE memoLocalId = :memoLocalId AND remoteName IS NOT NULL")
    suspend fun deleteSyncedForMemo(memoLocalId: String)
}
