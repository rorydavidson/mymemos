package com.keltruc.mymemos.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.keltruc.mymemos.database.entity.MemoRelationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationDao {
    @Query("SELECT * FROM memo_relations WHERE memoLocalId = :memoLocalId AND type = 'REFERENCE'")
    fun observeReferences(memoLocalId: String): Flow<List<MemoRelationEntity>>

    @Query("SELECT * FROM memo_relations WHERE memoLocalId = :memoLocalId AND type = 'REFERENCE'")
    suspend fun references(memoLocalId: String): List<MemoRelationEntity>

    /** Memos (local ids) that reference [remoteName]. */
    @Query("SELECT memoLocalId FROM memo_relations WHERE relatedRemoteName = :remoteName AND type = 'REFERENCE'")
    fun observeBacklinks(remoteName: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(relations: List<MemoRelationEntity>)

    @Query("DELETE FROM memo_relations WHERE memoLocalId = :memoLocalId")
    suspend fun deleteForMemo(memoLocalId: String)

    @Query("DELETE FROM memo_relations WHERE memoLocalId = :memoLocalId AND relatedRemoteName = :relatedRemoteName AND type = 'REFERENCE'")
    suspend fun deleteReference(memoLocalId: String, relatedRemoteName: String)
}
