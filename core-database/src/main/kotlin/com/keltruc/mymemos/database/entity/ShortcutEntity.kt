package com.keltruc.mymemos.database.entity

import androidx.room.Entity
import androidx.room.Index

/** Saved server-side filter (`users/x/shortcuts/y`), cached for the chip row. */
@Entity(tableName = "shortcuts", primaryKeys = ["accountId", "name"], indices = [Index("accountId")])
data class ShortcutEntity(
    val accountId: Long,
    val name: String,
    val title: String,
    val filter: String,
)
