package com.keltruc.mymemos.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A reference from one memo to another. Only the source side is a local row; the target is
 * held by server name so references to memos we have not pulled still display.
 */
@Entity(
    tableName = "memo_relations",
    primaryKeys = ["memoLocalId", "relatedRemoteName", "type"],
    foreignKeys = [
        ForeignKey(entity = MemoEntity::class, parentColumns = ["localId"], childColumns = ["memoLocalId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("relatedRemoteName")],
)
data class MemoRelationEntity(
    val memoLocalId: String,
    val relatedRemoteName: String,
    val relatedSnippet: String,
    /** REFERENCE or COMMENT, as the server names them. */
    val type: String,
)
