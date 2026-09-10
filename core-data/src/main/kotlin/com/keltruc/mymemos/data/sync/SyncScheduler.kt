package com.keltruc.mymemos.data.sync

import com.keltruc.mymemos.database.dao.AccountDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Catching up after the process is gone is the operating system's business: WorkManager on
 * Android, something else on a Mac. Only the two ways in are shared.
 */
interface BackgroundSync {
    /** Try again later, with backoff, once there is a network. */
    fun enqueueRetry(full: Boolean)

    /** Periodic catch-up while the app is closed. */
    fun ensurePeriodic()

    fun cancelAll()
}

/**
 * Two paths to a sync: an immediate in-process run, debounced so a burst of edits becomes one
 * push, while the app is alive; and [BackgroundSync] for catch-up and retries after it is not.
 *
 * The debounce is the part worth sharing, so it lives here and the scheduling does not.
 */
class SyncScheduler(
    private val engine: SyncEngine,
    private val accountDao: AccountDao,
    private val background: BackgroundSync,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var immediate: Job? = null

    /**
     * Run soon, once, e.g. after the user saves a memo. [afterMs] holds it back longer, which a
     * delete uses so there is time to take it back before it reaches the server. Any later call
     * replaces this one, so a delayed sync can still be brought forward by the next edit.
     */
    fun syncNow(full: Boolean = false, afterMs: Long = DEBOUNCE_MS) {
        immediate?.cancel()
        immediate = scope.launch {
            delay(afterMs)
            val accountId = accountDao.getActive()?.id ?: return@launch
            when (engine.sync(accountId, fullPull = full)) {
                SyncEngine.Outcome.Success, is SyncEngine.Outcome.AuthFailed -> Unit
                is SyncEngine.Outcome.Retry -> background.enqueueRetry(full)
            }
        }
    }

    fun ensurePeriodic() = background.ensurePeriodic()

    fun cancelAll() = background.cancelAll()

    private companion object {
        const val DEBOUNCE_MS = 400L
    }
}
