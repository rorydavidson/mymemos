package com.keltruc.mymemos.sync

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
import com.keltruc.mymemos.data.sync.BackgroundSync
import com.keltruc.mymemos.data.sync.SyncEngine
import com.keltruc.mymemos.database.dao.AccountDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

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

/** The Android half of [BackgroundSync]: catching up after the process is gone. */
class WorkManagerBackgroundSync(context: Context) : BackgroundSync {

    private val workManager = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun enqueueRetry(full: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_FULL to full))
            .build()
        workManager.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    override fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelAll() {
        workManager.cancelUniqueWork(NOW)
        workManager.cancelUniqueWork(PERIODIC)
    }

    private companion object {
        const val NOW = "sync-now"
        const val PERIODIC = "sync-periodic"
    }
}
