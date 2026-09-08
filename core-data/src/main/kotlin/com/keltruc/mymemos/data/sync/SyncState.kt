package com.keltruc.mymemos.data.sync

import java.time.Instant

data class SyncState(
    val running: Boolean = false,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val conflictCount: Int = 0,
    val lastSuccess: Instant? = null,
    val lastError: String? = null,
)

data class FailedOp(val id: Long, val memoLocalId: String, val type: String, val error: String?, val attempts: Int)
