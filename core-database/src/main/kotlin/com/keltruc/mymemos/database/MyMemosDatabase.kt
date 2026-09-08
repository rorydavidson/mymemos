package com.keltruc.mymemos.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.database.dao.AttachmentDao
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.database.entity.AccountEntity
import com.keltruc.mymemos.database.entity.AttachmentEntity
import com.keltruc.mymemos.database.entity.MemoEntity
import com.keltruc.mymemos.database.entity.MemoFtsEntity

@Database(
    entities = [AccountEntity::class, MemoEntity::class, MemoFtsEntity::class, AttachmentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MyMemosDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun memoDao(): MemoDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        const val NAME = "mymemos.db"
    }
}
