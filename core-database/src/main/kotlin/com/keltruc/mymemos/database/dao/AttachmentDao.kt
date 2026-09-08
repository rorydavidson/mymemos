package com.keltruc.mymemos.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.keltruc.mymemos.database.entity.AttachmentEntity

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE memoLocalId = :memoLocalId")
    suspend fun deleteForMemo(memoLocalId: String)

    @Query("SELECT * FROM attachments WHERE memoLocalId = :memoLocalId")
    suspend fun forMemo(memoLocalId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE localId = :localId")
    suspend fun byLocalId(localId: String): AttachmentEntity?

    @Update
    suspend fun update(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    /** Every attachment belonging to an account, so its cached files can be removed with it. */
    @Query("SELECT a.localId FROM attachments a JOIN memos m ON m.localId = a.memoLocalId WHERE m.accountId = :accountId")
    suspend fun localIdsForAccount(accountId: Long): List<String>
}
