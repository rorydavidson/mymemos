package com.keltruc.mymemos.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local memo templates. Placeholders like {{date}} are filled when inserted. */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val sortOrder: Int = 0,
)
