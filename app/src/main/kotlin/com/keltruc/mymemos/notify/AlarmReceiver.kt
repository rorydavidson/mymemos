package com.keltruc.mymemos.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.keltruc.mymemos.R
import com.keltruc.mymemos.data.config.ConfigRepository
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.repository.TemplateRepository
import com.keltruc.mymemos.model.Visibility
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlarmEntryPoint {
    fun config(): ConfigRepository
    fun memos(): MemoRepository
    fun accounts(): AccountRepository
    fun templates(): TemplateRepository
    fun notifier(): Notifier
    fun scheduler(): AlarmScheduler
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, AlarmEntryPoint::class.java)
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ID) ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    when (intent.action) {
                        AlarmScheduler.REMINDER -> fireReminder(context, entry, id)
                        AlarmScheduler.RECURRING -> fireRecurring(context, entry, id)
                    }
                }
            } finally {
                // Never let a bad alarm take the process down; the next reschedule will retry.
                runCatching { entry.scheduler().rescheduleNow() }
                result.finish()
            }
        }
    }

    private suspend fun fireReminder(context: Context, entry: AlarmEntryPoint, id: String) {
        val reminder = entry.config().current().reminders.firstOrNull { it.id == id } ?: return
        val account = entry.accounts().activeAccountOrNull() ?: return
        val localId = entry.memos().ensureLocal(account, reminder.memoRemoteName)
        val memo = localId?.let { entry.memos().observeMemoOnce(it) }
        val text = reminder.note.ifEmpty {
            memo?.let { m -> if (m.isLocked) context.getString(R.string.locked_memo) else m.displayContent.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }.orEmpty()
        }
        entry.notifier().post(Notifier.Channel.REMINDERS, id.hashCode(), context.getString(R.string.reminder_notification_title), text, localId)
        // One-off: drop it from the config so other devices stop showing it too.
        entry.config().update { c -> c.copy(reminders = c.reminders.filterNot { it.id == id }) }
    }

    private suspend fun fireRecurring(context: Context, entry: AlarmEntryPoint, templateTitle: String) {
        val account = entry.accounts().activeAccountOrNull() ?: return
        val template = entry.templates().templates.first().firstOrNull { it.title == templateTitle } ?: return
        val body = TemplateRepository.expand(template.body)
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val today = entry.memos().observeCreatedOn(account.id, LocalDate.now()).first()
        if (firstLine.isNotEmpty() && today.any { it.content.lineSequence().firstOrNull { l -> l.isNotBlank() } == firstLine }) return
        val localId = entry.memos().create(account.id, body, Visibility.PRIVATE)
        entry.notifier().post(Notifier.Channel.RECURRING, templateTitle.hashCode(), context.getString(R.string.recurring_created, templateTitle), firstLine, localId)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, AlarmEntryPoint::class.java)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { runCatching { entry.scheduler().rescheduleNow() } } finally { result.finish() }
        }
    }
}
