package com.sr2ma.daybook.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.dao.MeetingDao
import com.sr2ma.daybook.data.dao.TaskDao
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Task
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Re-schedules task and meeting reminder alarms after a device reboot.
 *
 * Alarms set with [android.app.AlarmManager] are cleared on reboot by Android.
 * This receiver listens for [Intent.ACTION_BOOT_COMPLETED] and restores all pending
 * alerts for open tasks and upcoming meetings.
 */
class BootReceiver(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BroadcastReceiver() {

    companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"

        fun isBootAction(action: String?): Boolean =
            action == Intent.ACTION_BOOT_COMPLETED ||
                action == ACTION_QUICKBOOT_POWERON ||
                action == ACTION_HTC_QUICKBOOT

        fun filterReschedulableTasks(tasks: List<Task>, today: LocalDate): List<Task> =
            tasks.filter { task ->
                task.isOpen && task.dueDate != null && !task.dueDate.isBefore(today)
            }

        fun filterReschedulableMeetings(meetings: List<Meeting>, today: LocalDate): List<Meeting> =
            meetings.filter { meeting ->
                !meeting.day.isBefore(today)
            }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (!isBootAction(intent?.action)) return

        ReminderNotificationManager.initChannels(context)

        val pendingResult = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                rescheduleAllAlarms(context)
            } catch (_: Throwable) {
                // Defensive: Ensure broadcast completes cleanly even on storage/alarm anomalies
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal fun rescheduleAllAlarms(
        context: Context,
        database: DaybookDatabase = DaybookDatabase.getInstance(context),
        today: LocalDate = LocalDate.now(),
    ) {
        // 1. Reschedule open tasks with a valid due date (today or later)
        val taskDao = TaskDao(database)
        val openTasks = filterReschedulableTasks(taskDao.all(), today)
        for (task in openTasks) {
            try {
                ReminderNotificationManager.scheduleTaskReminder(context, task)
            } catch (_: Throwable) {}
        }

        // 2. Reschedule upcoming meetings (today or later)
        val meetingDao = MeetingDao(database)
        val upcomingMeetings = filterReschedulableMeetings(meetingDao.all(), today)
        for (meeting in upcomingMeetings) {
            try {
                ReminderNotificationManager.scheduleMeetingReminder(context, meeting)
            } catch (_: Throwable) {}
        }
    }
}
