package com.keltruc.mymemos.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MemoEntity::class,
            parentColumns = ["localId"],
            childColumns = ["memoLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("memoLocalId"), Index("remoteName")],
)
data class AttachmentEntity(
    @PrimaryKey val localId: String,
    val memoLocalId: String,
    val remoteName: String?,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val externalLink: String?,
    val localPath: String?,
    val createTimeEpochMs: Long,
)
