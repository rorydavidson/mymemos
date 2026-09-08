package com.keltruc.mymemos.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.keltruc.mymemos.data.config.ConfigRepository
import com.keltruc.mymemos.data.repository.TemplateRepository
import com.keltruc.mymemos.model.AppConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the config memo's reminders and recurring templates into AlarmManager alarms.
 * Re-run whenever the config changes (which includes edits made on another device), on
 * boot and after an update. Exact alarms are used when the user has allowed them;
 * otherwise Android delivers within a window of several minutes.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ConfigRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private var known = setOf<Int>()

    fun start() {
        android.util.Log.i(TAG, "start")
        scope.launch {
            configRepository.config.collectLatest { config ->
                android.util.Log.i(TAG, "config emitted: ${config.reminders.size} reminders")
                runCatching { apply(config) }.onFailure { android.util.Log.w(TAG, "scheduling failed", it) }
            }
        }
    }

    suspend fun rescheduleNow() = apply(configRepository.config.first())

    @Synchronized
    private fun apply(config: AppConfig) {
        val now = System.currentTimeMillis()
        val next = mutableSetOf<Int>()
        for (r in config.reminders) {
            val code = codeFor(REMINDER, r.id)
            next += code
            if (r.atEpochMs > now) set(code, r.atEpochMs, Intent(context, AlarmReceiver::class.java).setAction(REMINDER).putExtra(EXTRA_ID, r.id))
        }
        for (t in config.recurring.filter { it.enabled }) {
            val code = codeFor(RECURRING, t.templateTitle)
            next += code
            set(code, nextOccurrence(t.hour, t.minute), Intent(context, AlarmReceiver::class.java).setAction(RECURRING).putExtra(EXTRA_ID, t.templateTitle))
        }
        // Cancel anything scheduled last time that is gone now.
        (known - next).forEach { code ->
            alarms.cancel(PendingIntent.getBroadcast(context, code, Intent(context, AlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        known = next
        android.util.Log.i(TAG, "scheduled ${config.reminders.size} reminders, ${config.recurring.count { it.enabled }} recurring; exact=${canScheduleExact()}")
    }

    private fun set(code: Int, at: Long, intent: Intent) {
        val pending = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    companion object {
        private const val TAG = "AlarmScheduler"
        const val REMINDER = "com.keltruc.mymemos.alarm.REMINDER"
        const val RECURRING = "com.keltruc.mymemos.alarm.RECURRING"
        const val EXTRA_ID = "id"

        fun codeFor(kind: String, id: String): Int = (kind + ":" + id).hashCode()

        fun nextOccurrence(hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
            var at = LocalDate.now(zone).atTime(LocalTime.of(hour, minute)).atZone(zone)
            if (!at.toInstant().isAfter(java.time.Instant.now())) at = at.plusDays(1)
            return at.toInstant().toEpochMilli()
        }
    }

    @Suppress("unused") private val keepTemplates = TemplateRepository::class
}
