package com.keltruc.mymemos.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.keltruc.mymemos.database.entity.AttachmentEntity

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE memoLocalId = :memoLocalId")
    suspend fun deleteForMemo(memoLocalId: String)

    @Query("SELECT * FROM attachments WHERE memoLocalId = :memoLocalId")
    suspend fun forMemo(memoLocalId: String): List<AttachmentEntity>
}
