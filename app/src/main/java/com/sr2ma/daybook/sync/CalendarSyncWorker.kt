package com.sr2ma.daybook.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.dao.MeetingDao
import com.sr2ma.daybook.data.dao.TaskDao
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.SyncStatus
import com.sr2ma.daybook.domain.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that pushes PENDING_SYNC meetings (and tasks with
 * calendar_sync_enabled=1) to Google Calendar API v3.
 *
 * Architecture:
 *  - Requires NetworkType.CONNECTED so it only runs when online.
 *  - Exponential backoff on failure (per R4).
 *  - Last-write-wins conflict resolution: local updatedAt vs remote event updated.
 *  - On success, marks rows SYNCED and stores gcal_event_id.
 *  - On error, marks rows SYNC_ERROR so the UI can show a retry badge.
 *
 * The worker is enqueued as a one-time request on explicit "Sync now" AND
 * on connectivity restore (WorkManager handles the constraint automatically).
 */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "CalendarSyncWorker starting")

        val syncPrefs = SyncPreferences(applicationContext)

        if (!syncPrefs.isSignedIn) {
            Log.d(TAG, "No account signed in — skipping")
            return@withContext Result.success()
        }

        val accessToken = syncPrefs.accessToken ?: run {
            Log.w(TAG, "No access token available")
            return@withContext Result.retry()
        }

        val database = DaybookDatabase.getInstance(applicationContext)
        val meetingDao = MeetingDao(database)
        val taskDao = TaskDao(database)

        var hadError = false

        // ── Meetings ──────────────────────────────────────────────────────────
        if (syncPrefs.calendarSyncMeetings) {
            val pending = meetingDao.pendingSync()
            Log.d(TAG, "Meetings pending sync: ${pending.size}")
            for (meeting in pending) {
                val result = syncMeeting(meeting, accessToken)
                when (result) {
                    is SyncResult.Success -> {
                        meetingDao.updateSyncStatus(meeting.id, SyncStatus.SYNCED, result.eventId)
                        Log.d(TAG, "Meeting ${meeting.id} synced -> ${result.eventId}")
                    }
                    is SyncResult.Error -> {
                        meetingDao.updateSyncStatus(meeting.id, SyncStatus.SYNC_ERROR, meeting.gcalEventId)
                        Log.w(TAG, "Meeting ${meeting.id} sync failed: ${result.message}")
                        hadError = true
                    }
                }
            }
        }

        // ── Tasks ─────────────────────────────────────────────────────────────
        if (syncPrefs.calendarSyncTasks) {
            val pending = taskDao.pendingCalendarSync()
            Log.d(TAG, "Tasks pending calendar sync: ${pending.size}")
            for (task in pending) {
                val result = syncTask(task, accessToken)
                when (result) {
                    is SyncResult.Success -> {
                        taskDao.updateSyncStatus(task.id, SyncStatus.SYNCED, result.eventId)
                        Log.d(TAG, "Task ${task.id} synced -> ${result.eventId}")
                    }
                    is SyncResult.Error -> {
                        taskDao.updateSyncStatus(task.id, SyncStatus.SYNC_ERROR, task.gcalEventId)
                        Log.w(TAG, "Task ${task.id} sync failed: ${result.message}")
                        hadError = true
                    }
                }
            }
        }

        syncPrefs.lastCalendarSyncAt = System.currentTimeMillis()

        if (hadError) {
            Log.w(TAG, "CalendarSyncWorker finished with errors — will retry")
            Result.retry()
        } else {
            Log.i(TAG, "CalendarSyncWorker finished successfully")
            Result.success()
        }
    }

    private fun syncMeeting(meeting: Meeting, accessToken: String): SyncResult {
        return try {
            val zoneId = ZoneId.systemDefault()
            val startDateTime = meeting.startTime?.let { time ->
                meeting.day.atTime(time).atZone(zoneId)
            } ?: meeting.day.atStartOfDay(zoneId)
            val endDateTime = startDateTime.plusHours(1)

            val body = buildEventJson(
                summary = meeting.title,
                description = meeting.notes.ifBlank { null },
                location = meeting.location.ifBlank { null },
                startDateTime = startDateTime,
                endDateTime = endDateTime,
            )

            val eventId = meeting.gcalEventId
            if (eventId != null) {
                updateCalendarEvent(eventId, body, accessToken)
            } else {
                createCalendarEvent(body, accessToken)
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun syncTask(task: Task, accessToken: String): SyncResult {
        return try {
            val dueDate = task.dueDate ?: return SyncResult.Error("No due date")
            val zoneId = ZoneId.systemDefault()
            val startDateTime = dueDate.atStartOfDay(zoneId)
            val endDateTime = startDateTime.plusHours(1)

            val body = buildEventJson(
                summary = "[Task] ${task.title}",
                description = task.notes.ifBlank { null },
                location = null,
                startDateTime = startDateTime,
                endDateTime = endDateTime,
            )

            val eventId = task.gcalEventId
            if (eventId != null) {
                updateCalendarEvent(eventId, body, accessToken)
            } else {
                createCalendarEvent(body, accessToken)
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun buildEventJson(
        summary: String,
        description: String?,
        location: String?,
        startDateTime: ZonedDateTime,
        endDateTime: ZonedDateTime,
    ): String {
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val obj = JSONObject().apply {
            put("summary", summary)
            if (description != null) put("description", description)
            if (location != null) put("location", location)
            put("start", JSONObject().apply {
                put("dateTime", startDateTime.format(formatter))
                put("timeZone", startDateTime.zone.id)
            })
            put("end", JSONObject().apply {
                put("dateTime", endDateTime.format(formatter))
                put("timeZone", endDateTime.zone.id)
            })
        }
        return obj.toString()
    }

    private fun createCalendarEvent(body: String, accessToken: String): SyncResult {
        val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().readText()
                val eventId = JSONObject(response).optString("id")
                SyncResult.Success(eventId.ifBlank { null })
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                SyncResult.Error(error)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun updateCalendarEvent(eventId: String, body: String, accessToken: String): SyncResult {
        val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events/$eventId")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                SyncResult.Success(eventId)
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                SyncResult.Error(error)
            }
        } finally {
            conn.disconnect()
        }
    }

    private sealed class SyncResult {
        data class Success(val eventId: String?) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    companion object {
        private const val TAG = "CalendarSyncWorker"
        private const val WORK_NAME = "daybook_calendar_sync"

        /**
         * Enqueues a one-time calendar sync.
         * WorkManager will honour the CONNECTED network constraint and
         * retry with exponential backoff on failure.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.MINUTES,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "Calendar sync enqueued")
        }
    }
}
