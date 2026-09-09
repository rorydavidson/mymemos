package com.keltruc.mymemos.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reactions",
    foreignKeys = [
        ForeignKey(entity = MemoEntity::class, parentColumns = ["localId"], childColumns = ["memoLocalId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("memoLocalId")],
)
data class ReactionEntity(
    @PrimaryKey val localId: String,
    val memoLocalId: String,
    /** Server name `memos/x/reactions/y`; null while queued. */
    val remoteName: String?,
    val creator: String,
    val reactionType: String,
    val createTimeEpochMs: Long,
)
