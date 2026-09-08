package com.keltruc.mymemos.database.entity

import androidx.room.Entity
import androidx.room.Fts4

/** Full-text index over memo content, kept in step with [MemoEntity] by Room. */
@Fts4(contentEntity = MemoEntity::class)
@Entity(tableName = "memos_fts")
data class MemoFtsEntity(
    val localId: String,
    val content: String,
    val tagsJoined: String,
)
