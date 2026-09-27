package com.sr2ma.daybook.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderNotificationManager {

    const val CHANNEL_REMINDERS = "daybook_reminders"
    const val CHANNEL_INGESTION = "daybook_ingestion"

    const val EXTRA_TYPE = "extra_type"
    const val EXTRA_ID = "extra_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_SUBTITLE = "extra_subtitle"

    const val TYPE_TASK = "TASK"
    const val TYPE_MEETING = "MEETING"
    const val TYPE_MESSAGE = "MESSAGE"

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.notif_channel_reminders_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notif_channel_reminders_desc)
                    enableVibration(true)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_INGESTION,
                    "Daybook Ingestion Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alerts for actionable items detected from Gmail or WhatsApp"
                }
            )
        }
    }

    /**
     * Schedules a reminder alert for a task at 9:00 AM on the due date.
     */
    fun scheduleTaskReminder(context: Context, task: Task) {
        val dueDate = task.dueDate ?: return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var targetDateTime = LocalDateTime.of(dueDate, LocalTime.of(9, 0))
        if (targetDateTime.isBefore(now)) {
            if (dueDate == LocalDate.now(zone)) {
                targetDateTime = now.plusHours(1)
            } else {
                return
            }
        }

        val triggerMillis = targetDateTime.atZone(zone).toInstant().toEpochMilli()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_TASK)
            putExtra(EXTRA_ID, task.id)
            putExtra(EXTRA_TITLE, task.title)
            putExtra(EXTRA_SUBTITLE, "Due ${task.dueDate} • Priority: ${task.priority.name}")
        }
        val requestCode = (task.id * 10 + 1).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        scheduleAlarm(context, triggerMillis, pendingIntent)
    }

    fun scheduleTaskReminder(context: Context, taskId: Long, title: String, dueDate: LocalDate) {
        scheduleTaskReminder(context, Task(id = taskId, title = title, dueDate = dueDate))
    }

    fun cancelTaskReminder(context: Context, taskId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val requestCode = (taskId * 10 + 1).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Schedules an advance alert 15 minutes before the meeting start time.
     */
    fun scheduleMeetingReminder(context: Context, meeting: Meeting) {
        val date = meeting.day
        val time = meeting.startTime ?: LocalTime.of(10, 0)
        val zone = ZoneId.systemDefault()
        val meetingDateTime = LocalDateTime.of(date, time)
        val reminderDateTime = meetingDateTime.minusMinutes(15)
        val now = LocalDateTime.now(zone)
        if (reminderDateTime.isBefore(now)) {
            return
        }

        val triggerMillis = reminderDateTime.atZone(zone).toInstant().toEpochMilli()
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_MEETING)
            putExtra(EXTRA_ID, meeting.id)
            putExtra(EXTRA_TITLE, meeting.title)
            val who = if (meeting.attendees.isNotBlank()) " with ${meeting.attendees}" else ""
            putExtra(EXTRA_SUBTITLE, "Starting at $time on $date$who")
        }
        val requestCode = (meeting.id * 10 + 2).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        scheduleAlarm(context, triggerMillis, pendingIntent)
    }

    fun scheduleMeetingReminder(context: Context, meetingId: Long, title: String, meetingDay: LocalDate) {
        scheduleMeetingReminder(context, Meeting(id = meetingId, title = title, day = meetingDay))
    }

    fun cancelMeetingReminder(context: Context, meetingId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val requestCode = (meetingId * 10 + 2).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun scheduleAlarm(context: Context, triggerMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }
}
