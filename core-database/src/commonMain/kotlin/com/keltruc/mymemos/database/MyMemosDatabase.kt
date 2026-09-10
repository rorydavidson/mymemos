package com.keltruc.mymemos.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.database.dao.AttachmentDao
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.database.dao.PendingOpDao
import com.keltruc.mymemos.database.dao.ReactionDao
import com.keltruc.mymemos.database.dao.RelationDao
import com.keltruc.mymemos.database.dao.ShortcutDao
import com.keltruc.mymemos.database.dao.TemplateDao
import com.keltruc.mymemos.database.entity.AccountEntity
import com.keltruc.mymemos.database.entity.AttachmentEntity
import com.keltruc.mymemos.database.entity.MemoEntity
import com.keltruc.mymemos.database.entity.MemoFtsEntity
import com.keltruc.mymemos.database.entity.MemoRelationEntity
import com.keltruc.mymemos.database.entity.PendingOpEntity
import com.keltruc.mymemos.database.entity.ReactionEntity
import com.keltruc.mymemos.database.entity.ShortcutEntity
import com.keltruc.mymemos.database.entity.TemplateEntity

@Database(
    entities = [
        AccountEntity::class, MemoEntity::class, MemoFtsEntity::class, AttachmentEntity::class, PendingOpEntity::class,
        MemoRelationEntity::class, ReactionEntity::class, ShortcutEntity::class, TemplateEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@ConstructedBy(MyMemosDatabaseConstructor::class)
abstract class MyMemosDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun memoDao(): MemoDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun pendingOpDao(): PendingOpDao
    abstract fun relationDao(): RelationDao
    abstract fun reactionDao(): ReactionDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val NAME = "mymemos.db"
    }
}

/**
 * Room generates the actual for each target. The "no actual" warning is expected: the
 * compiler plugin supplies it, which is why this is suppressed rather than written.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object MyMemosDatabaseConstructor : RoomDatabaseConstructor<MyMemosDatabase> {
    override fun initialize(): MyMemosDatabase
}
