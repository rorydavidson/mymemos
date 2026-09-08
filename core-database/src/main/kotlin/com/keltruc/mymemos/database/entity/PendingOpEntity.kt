package com.keltruc.mymemos.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One queued change waiting to be pushed. Ops are applied in id order per account so a
 * memo's CREATE always lands before its later edits and attachment uploads.
 */
@Entity(
    tableName = "pending_ops",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index("memoLocalId")],
)
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val memoLocalId: String,
    /** One of the [Type] constants. */
    val type: String,
    /** Op-specific JSON payload; see the sync engine for shapes. */
    val payloadJson: String = "",
    val attempts: Int = 0,
    val lastError: String? = null,
    /** True once the server rejected this op for good (4xx other than 401/409). */
    val failed: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    object Type {
        const val CREATE = "CREATE"
        const val UPDATE_CONTENT = "UPDATE_CONTENT"
        const val SET_PINNED = "SET_PINNED"
        const val SET_VISIBILITY = "SET_VISIBILITY"
        const val SET_STATE = "SET_STATE"
        const val DELETE = "DELETE"
        const val ADD_ATTACHMENT = "ADD_ATTACHMENT"
        const val REMOVE_ATTACHMENT = "REMOVE_ATTACHMENT"
    }
}
