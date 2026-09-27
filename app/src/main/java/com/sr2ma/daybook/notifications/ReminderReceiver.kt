package com.sr2ma.daybook.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sr2ma.daybook.MainActivity
import com.sr2ma.daybook.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ReminderNotificationManager.initChannels(context)

        val type = intent.getStringExtra(ReminderNotificationManager.EXTRA_TYPE) ?: ReminderNotificationManager.TYPE_TASK
        val id = intent.getLongExtra(ReminderNotificationManager.EXTRA_ID, 0L)
        val title = intent.getStringExtra(ReminderNotificationManager.EXTRA_TITLE) ?: "Reminder"
        val subtitle = intent.getStringExtra(ReminderNotificationManager.EXTRA_SUBTITLE) ?: ""

        val openIntent = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notifTitle = when (type) {
            ReminderNotificationManager.TYPE_MEETING -> "Upcoming Meeting: $title"
            ReminderNotificationManager.TYPE_MESSAGE -> "Action Item: $title"
            else -> "Task Reminder: $title"
        }

        val notification = NotificationCompat.Builder(context, ReminderNotificationManager.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_today)
            .setContentTitle(notifTitle)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notifId = (20000 + (id % 10000)).toInt()
        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // Notification permission not granted
        }
    }
}
