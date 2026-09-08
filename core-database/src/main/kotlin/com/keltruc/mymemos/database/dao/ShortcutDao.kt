package com.keltruc.mymemos.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.keltruc.mymemos.database.entity.ShortcutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts WHERE accountId = :accountId ORDER BY title")
    fun observe(accountId: Long): Flow<List<ShortcutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(shortcuts: List<ShortcutEntity>)

    @Query("DELETE FROM shortcuts WHERE accountId = :accountId")
    suspend fun deleteAll(accountId: Long)

    @Transaction
    suspend fun replaceAll(accountId: Long, shortcuts: List<ShortcutEntity>) {
        deleteAll(accountId)
        upsertAll(shortcuts)
    }
}
