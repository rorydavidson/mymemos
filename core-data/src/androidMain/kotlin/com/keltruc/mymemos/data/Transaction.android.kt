package com.keltruc.mymemos.data

import androidx.room.withTransaction
import com.keltruc.mymemos.database.MyMemosDatabase

/** Unchanged from what the app has always used. */
internal actual suspend fun <R> MyMemosDatabase.transaction(block: suspend () -> R): R =
    withTransaction(block)
