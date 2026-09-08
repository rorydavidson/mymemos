package com.keltruc.mymemos.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.keltruc.mymemos.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE serverUrl = :serverUrl AND userResourceName = :userResourceName")
    suspend fun find(serverUrl: String, userResourceName: String): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET isActive = (id = :id)")
    suspend fun setActive(id: Long)

    @Query("UPDATE accounts SET lastSyncEpochMs = :epochMs WHERE id = :id")
    suspend fun setLastSync(id: Long, epochMs: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    suspend fun upsertAndActivate(account: AccountEntity): Long {
        val existing = find(account.serverUrl, account.userResourceName)
        val id = if (existing == null) {
            insert(account)
        } else {
            update(account.copy(id = existing.id, lastSyncEpochMs = existing.lastSyncEpochMs))
            existing.id
        }
        setActive(id)
        return id
    }
}
