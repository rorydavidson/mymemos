package com.keltruc.mymemos.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["serverUrl", "userResourceName"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverUrl: String,
    /** Server resource name, e.g. `users/rory`. */
    val userResourceName: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val role: String,
    val authMethod: String,
    val serverVersion: String,
    /** Wall-clock of the last successful pull, used for delta sync. Null = never. */
    val lastSyncEpochMs: Long? = null,
    val isActive: Boolean = false,
)
