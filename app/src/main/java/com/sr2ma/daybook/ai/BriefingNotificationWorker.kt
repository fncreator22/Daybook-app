package com.sr2ma.daybook.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sr2ma.daybook.MainActivity
import com.sr2ma.daybook.R
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.DaybookRepository
import com.sr2ma.daybook.domain.BriefingWriter
import com.sr2ma.daybook.domain.TodayBuilder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Fires every morning at ~7:30 AM to post an expandable "Today's plan" notification.
 *
 * Uses BriefingWriter (deterministic, no LLM) to generate the briefing text.
 * Two action buttons: open the app, or dismiss.
 *
 * Scheduling: [schedule] registers a 24-hour periodic job via WorkManager.
 * First run is aligned to the next 7:30 AM wall-clock time; subsequent runs
 * repeat every 24 hours (WorkManager keeps ±15 min flex window).
 *
 * Required: POST_NOTIFICATIONS permission (Android 13+) already declared in manifest.
 */
class BriefingNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        // Ensure notification channel exists (idempotent)
        createChannels(ctx)

        val db   = DaybookDatabase.getInstance(ctx)
        val repo = DaybookRepository(db)
        repo.refreshAll()

        val today = LocalDate.now()
        val tasks    = repo.tasks.value
        val meetings = repo.meetings.value
        val log      = repo.logEntries.value

        val board   = TodayBuilder.build(tasks, meetings, log, today, ZoneId.systemDefault())
        val briefing = BriefingWriter.write(board)

        // Build a human-readable day label  e.g. "Sunday, 22 Sept"
        val dayLabel = "${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, " +
            "${today.dayOfMonth} " +
            today.month.name.lowercase().replaceFirstChar { it.uppercase() }

        val title = ctx.getString(R.string.notif_briefing_title, dayLabel)

        // Tap → open app
        val openIntent = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_BRIEFING)
            .setSmallIcon(R.drawable.ic_today)
            .setContentTitle(title)
            .setContentText(briefing.take(80) + if (briefing.length > 80) "…" else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(briefing))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setTicker(ctx.getString(R.string.notif_briefing_ticker))
            .build()

        // Only post if we have permission (Android 13+ check handled by NotificationManagerCompat)
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID_BRIEFING, notification)
        } catch (_: SecurityException) {
            // User hasn't granted POST_NOTIFICATIONS — silent skip (will show on next grant)
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME       = "daybook_morning_briefing"
        private const val CHANNEL_BRIEFING = "daybook_briefing"
        private const val NOTIF_ID_BRIEFING = 1001

        fun createChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_BRIEFING,
                        ctx.getString(R.string.notif_channel_briefing_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = ctx.getString(R.string.notif_channel_briefing_desc)
                    }
                )
                nm.createNotificationChannel(
                    NotificationChannel(
                        "daybook_reminders",
                        ctx.getString(R.string.notif_channel_reminders_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = ctx.getString(R.string.notif_channel_reminders_desc)
                    }
                )
            }
        }

        /**
         * Schedule (or re-schedule) the briefing worker.
         * First firing is aligned to the next 07:30 local time.
         * After that: every 24 hours, WorkManager ±15 min flex.
         */
        fun schedule(context: Context) {
            val now    = LocalTime.now()
            val target = LocalTime.of(7, 30)
            // Delay until next 07:30; if already past 07:30 today → next day
            val delayMinutes = if (now.isBefore(target)) {
                Duration.between(now, target).toMinutes()
            } else {
                Duration.between(now, target).toMinutes() + TimeUnit.HOURS.toMinutes(24)
            }

            val request = PeriodicWorkRequestBuilder<BriefingNotificationWorker>(
                24, TimeUnit.HOURS,
                15, TimeUnit.MINUTES,   // flex window
            )
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
