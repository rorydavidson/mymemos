package com.keltruc.mymemos.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.keltruc.mymemos.MainActivity
import com.keltruc.mymemos.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {
    enum class Channel(val id: String, val nameRes: Int, val importance: Int) {
        REMINDERS("reminders", R.string.channel_reminders, NotificationManager.IMPORTANCE_HIGH),
        DIGEST("digest", R.string.channel_digest, NotificationManager.IMPORTANCE_DEFAULT),
        RECURRING("recurring", R.string.channel_recurring, NotificationManager.IMPORTANCE_LOW),
    }

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        Channel.entries.forEach { c ->
            manager.createNotificationChannel(NotificationChannel(c.id, context.getString(c.nameRes), c.importance))
        }
    }

    fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts a notification that opens the memo (or the app) when tapped. */
    fun post(channel: Channel, id: Int, title: String, text: String, memoLocalId: String? = null) {
        if (!canPost()) return
        ensureChannels()
        val intent = Intent(context, MainActivity::class.java).apply {
            if (memoLocalId != null) {
                action = MainActivity.ACTION_OPEN_MEMO
                putExtra(MainActivity.EXTRA_MEMO_LOCAL_ID, memoLocalId)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_widget_checkbox)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
