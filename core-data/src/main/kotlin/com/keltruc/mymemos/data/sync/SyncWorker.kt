package com.keltruc.mymemos.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.keltruc.mymemos.database.dao.AccountDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
    private val accountDao: AccountDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val full = inputData.getBoolean(KEY_FULL, false)
        val accountId = inputData.getLong(KEY_ACCOUNT, -1).takeIf { it >= 0 } ?: accountDao.getActive()?.id ?: return Result.success()
        return when (engine.sync(accountId, fullPull = full)) {
            SyncEngine.Outcome.Success -> Result.success()
            is SyncEngine.Outcome.AuthFailed -> Result.failure()
            is SyncEngine.Outcome.Retry -> if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_FULL = "full"
        const val KEY_ACCOUNT = "account"
    }
}

/**
 * Two paths to a sync: an immediate in-process run (debounced, so a burst of edits becomes
 * one push) while the app is alive, and WorkManager for background catch-up and retries
 * after the process is gone.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: SyncEngine,
    private val accountDao: AccountDao,
) {
    private val workManager get() = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
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
                is SyncEngine.Outcome.Retry -> enqueueBackground(full)
            }
        }
    }

    private fun enqueueBackground(full: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_FULL to full))
            .build()
        workManager.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Background catch-up while the app is closed. */
    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(NOW)
        workManager.cancelUniqueWork(PERIODIC)
    }

    private companion object {
        const val NOW = "sync-now"
        const val PERIODIC = "sync-periodic"
        const val DEBOUNCE_MS = 400L
    }
}
