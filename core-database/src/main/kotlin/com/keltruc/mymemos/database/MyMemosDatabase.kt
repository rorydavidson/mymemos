package com.keltruc.mymemos.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.database.dao.AttachmentDao
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.database.dao.PendingOpDao
import com.keltruc.mymemos.database.dao.ReactionDao
import com.keltruc.mymemos.database.dao.RelationDao
import com.keltruc.mymemos.database.dao.ShortcutDao
import com.keltruc.mymemos.database.entity.AccountEntity
import com.keltruc.mymemos.database.entity.AttachmentEntity
import com.keltruc.mymemos.database.entity.MemoEntity
import com.keltruc.mymemos.database.entity.MemoFtsEntity
import com.keltruc.mymemos.database.entity.MemoRelationEntity
import com.keltruc.mymemos.database.entity.PendingOpEntity
import com.keltruc.mymemos.database.entity.ReactionEntity
import com.keltruc.mymemos.database.entity.ShortcutEntity

@Database(
    entities = [
        AccountEntity::class, MemoEntity::class, MemoFtsEntity::class, AttachmentEntity::class, PendingOpEntity::class,
        MemoRelationEntity::class, ReactionEntity::class, ShortcutEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class MyMemosDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun memoDao(): MemoDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun pendingOpDao(): PendingOpDao
    abstract fun relationDao(): RelationDao
    abstract fun reactionDao(): ReactionDao
    abstract fun shortcutDao(): ShortcutDao

    companion object {
        const val NAME = "mymemos.db"
    }
}
