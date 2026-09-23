package com.sr2ma.daybook.sync

import android.content.Context
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.dao.MeetingDao
import com.sr2ma.daybook.data.dao.TaskDao
import com.sr2ma.daybook.domain.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central coordinator for all sync operations.
 *
 * Lives in [AppContainer] (lazy-initialised, zero cost until first use).
 * Exposes suspend functions that the ViewModel calls from its scope.
 *
 * Sync is fully opt-in: every method checks [SyncPreferences] before doing
 * network work. The rule engine (AgentEngine, NightlyAgentWorker) continues
 * to run regardless of sync state.
 */
class SyncManager(
    private val context: Context,
    val syncPrefs: SyncPreferences,
    val authClient: GoogleAuthClient,
) {

    // ── Auth ─────────────────────────────────────────────────────────────────

    suspend fun signIn(): String? = authClient.signIn()

    suspend fun signOut() {
        authClient.signOut()
        // Cancel all sync workers when signed out.
        DriveBackupWorker.cancelDaily(context)
    }

    // ── Feature toggles ──────────────────────────────────────────────────────

    /**
     * Enables or disables calendar sync for meetings.
     * When enabled, marks all existing meetings PENDING_SYNC so they are
     * picked up on the next worker run.
     */
    suspend fun setCalendarSyncMeetings(enabled: Boolean) {
        syncPrefs.calendarSyncMeetings = enabled
        if (enabled) {
            markAllMeetingsPending()
            CalendarSyncWorker.enqueue(context)
        }
    }

    /**
     * Enables or disables calendar sync for tasks that have a due date and
     * calendar_sync_enabled = true.
     */
    fun setCalendarSyncTasks(enabled: Boolean) {
        syncPrefs.calendarSyncTasks = enabled
        if (enabled) {
            CalendarSyncWorker.enqueue(context)
        }
    }

    /** Enables or disables daily Drive auto-backup. */
    fun setDriveAutoBackup(enabled: Boolean) {
        syncPrefs.driveAutoBackup = enabled
        if (enabled) {
            DriveBackupWorker.scheduleDaily(context)
        } else {
            DriveBackupWorker.cancelDaily(context)
        }
    }

    // ── Manual sync/backup triggers ──────────────────────────────────────────

    /** Enqueues an immediate calendar sync (user pressed "Sync now"). */
    fun syncNow() {
        CalendarSyncWorker.enqueue(context)
    }

    /** Enqueues an immediate Drive backup (user pressed "Back up now"). */
    fun backupNow() {
        DriveBackupWorker.enqueueOnce(context)
    }

    /**
     * Lists available Drive backups.
     * Returns empty list if not signed in or network call fails.
     */
    suspend fun listDriveBackups(): List<DriveBackupWorker.DriveFile> = withContext(Dispatchers.IO) {
        val token = syncPrefs.accessToken ?: return@withContext emptyList()
        try {
            DriveBackupWorker.listBackups(token)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Downloads and restores a Drive backup.
     * Returns true on success. The app must restart to pick up the new DB.
     */
    suspend fun restoreBackup(fileId: String, dbKey: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val token = syncPrefs.accessToken ?: return@withContext false
        val destDir = context.filesDir
        val tempFile = try {
            DriveBackupWorker.downloadBackup(fileId, token, destDir)
        } catch (e: Exception) {
            null
        } ?: return@withContext false

        val liveDbFile = context.getDatabasePath(DaybookDatabase.DATABASE_NAME)
        DriveBackupWorker.restoreBackup(tempFile, liveDbFile, dbKey)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun markAllMeetingsPending() = withContext(Dispatchers.IO) {
        val database = DaybookDatabase.getInstance(context)
        val meetingDao = MeetingDao(database)
        val all = meetingDao.all()
        all.filter { it.syncStatus == SyncStatus.LOCAL_ONLY }.forEach { meeting ->
            meetingDao.updateSyncStatus(meeting.id, SyncStatus.PENDING_SYNC, meeting.gcalEventId)
        }
    }

    companion object {
        private const val TAG = "SyncManager"
        // OAuth 2.0 Web client ID — needs to be set in build config or resources.
        // Using a placeholder here; the actual value comes from the Google Cloud Console
        // and is set in the app's strings.xml or BuildConfig.
        const val GOOGLE_SERVER_CLIENT_ID_PLACEHOLDER =
            "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    }
}
