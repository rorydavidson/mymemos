package com.keltruc.mymemos.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.keltruc.mymemos.database.MyMemosDatabase

internal actual suspend fun <R> MyMemosDatabase.transaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }
