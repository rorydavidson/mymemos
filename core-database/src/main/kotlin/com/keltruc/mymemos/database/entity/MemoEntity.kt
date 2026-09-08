package com.keltruc.mymemos.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memos",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("accountId"),
        Index(value = ["accountId", "remoteName"], unique = true),
        Index(value = ["accountId", "state", "pinned", "createTimeEpochMs"]),
        Index("syncStatus"),
    ],
)
data class MemoEntity(
    @PrimaryKey val localId: String,
    val accountId: Long,
    val remoteName: String?,
    val creator: String?,
    val content: String,
    val visibility: String,
    val state: String,
    val pinned: Boolean,
    /** Tags joined with [TAG_SEPARATOR] so they can be searched without a join table. */
    val tagsJoined: String,
    val createTimeEpochMs: Long,
    val updateTimeEpochMs: Long,
    val snippet: String,
    val hasTaskList: Boolean,
    val hasIncompleteTasks: Boolean,
    val hasLink: Boolean,
    val hasCode: Boolean,
    val locationPlaceholder: String?,
    val latitude: Double?,
    val longitude: Double?,
    val syncStatus: String,
    /**
     * Server `updateTime` when this row was last reconciled. On push, if the server has
     * moved past this, the change needs a merge rather than a blind overwrite.
     */
    val baseUpdateTimeEpochMs: Long?,
) {
    companion object {
        /** ASCII unit separator (0x1F): cannot appear in a tag, unlike commas or spaces. */
        val TAG_SEPARATOR: String = 31.toChar().toString()
    }
}
