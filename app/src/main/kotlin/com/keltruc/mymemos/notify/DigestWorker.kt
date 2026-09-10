package com.keltruc.mymemos.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.keltruc.mymemos.R
import com.keltruc.mymemos.data.config.ConfigRepository
import com.keltruc.mymemos.data.notify.Digest
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Sunday evening summary. Everything is computed from the local database. */
@HiltWorker
class DigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accounts: AccountRepository,
    private val memos: MemoRepository,
    private val config: ConfigRepository,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!config.current().weeklyDigest) return Result.success()
        val account = accounts.activeAccountOrNull() ?: return Result.success()
        val zone = TimeZone.currentSystemDefault()
        val summary = Digest.summarise(
            all = memos.allForDigest(account.id),
            activeDays = memos.observeActiveDays(account.id).first(),
            today = Clock.System.todayIn(zone),
            zone = zone,
        )
        val ctx = applicationContext
        val text = Digest.text(summary, AndroidDigestLabels(ctx))
        notifier.post(Notifier.Channel.DIGEST, 7_001, ctx.getString(R.string.digest_title), text)
        return Result.success()
    }
}

@Singleton
class DigestScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun enable() {
        val now = ZonedDateTime.now()
        var next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(LocalTime.of(18, 0))
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val request = PeriodicWorkRequestBuilder<DigestWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun disable() = WorkManager.getInstance(context).cancelUniqueWork(NAME)

    private companion object { const val NAME = "weekly-digest" }
}
