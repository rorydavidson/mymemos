package com.keltruc.mymemos.data

import com.keltruc.mymemos.database.MyMemosDatabase

/**
 * One database transaction.
 *
 * This is expect/actual rather than the multiplatform API everywhere because Room's Android
 * `withTransaction` is re-entrant and years-proven, and the sync engine nests writes. Swapping
 * Android onto different transaction semantics to save a few lines would risk a deadlock in the
 * one place where a deadlock costs someone their notes.
 */
internal expect suspend fun <R> MyMemosDatabase.transaction(block: suspend () -> R): R
