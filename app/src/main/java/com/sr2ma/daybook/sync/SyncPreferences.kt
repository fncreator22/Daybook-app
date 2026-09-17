package com.sr2ma.daybook.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores all sync-related preferences using EncryptedSharedPreferences backed by
 * Android Keystore.  No token is ever written to plaintext SharedPreferences or
 * to any log statement.
 *
 * Toggle settings (calendarSyncMeetings, calendarSyncTasks, driveAutoBackup) are
 * stored here alongside the account email so the Settings screen has one source
 * of truth, and the Worker has access without needing a ViewModel.
 */
class SyncPreferences(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ── Account ──────────────────────────────────────────────────────────────

    var accountEmail: String?
        get() = prefs.getString(KEY_ACCOUNT_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT_EMAIL, value).apply()

    // ── OAuth tokens — never logged ───────────────────────────────────────────

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var tokenExpiry: Long
        get() = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_TOKEN_EXPIRY, value).apply()

    // ── Feature toggles (default OFF per AGENTS.md requirements) ─────────────

    var calendarSyncMeetings: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_SYNC_MEETINGS, false)
        set(value) = prefs.edit().putBoolean(KEY_CALENDAR_SYNC_MEETINGS, value).apply()

    var calendarSyncTasks: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_SYNC_TASKS, false)
        set(value) = prefs.edit().putBoolean(KEY_CALENDAR_SYNC_TASKS, value).apply()

    var driveAutoBackup: Boolean
        get() = prefs.getBoolean(KEY_DRIVE_AUTO_BACKUP, false)
        set(value) = prefs.edit().putBoolean(KEY_DRIVE_AUTO_BACKUP, value).apply()

    // ── Last-sync timestamps ──────────────────────────────────────────────────

    var lastCalendarSyncAt: Long
        get() = prefs.getLong(KEY_LAST_CALENDAR_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CALENDAR_SYNC, value).apply()

    var lastDriveBackupAt: Long
        get() = prefs.getLong(KEY_LAST_DRIVE_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_DRIVE_BACKUP, value).apply()

    // ── Sign-out ──────────────────────────────────────────────────────────────

    /**
     * Clears all auth tokens and account info.
     * Toggles are kept so the user's preferences survive re-auth.
     */
    fun clearAuth() {
        prefs.edit()
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .apply()
    }

    /** Convenience: is a user currently signed in? */
    val isSignedIn: Boolean
        get() = accountEmail != null && accessToken != null

    companion object {
        private const val PREFS_NAME = "daybook_sync_prefs"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_CALENDAR_SYNC_MEETINGS = "calendar_sync_meetings"
        private const val KEY_CALENDAR_SYNC_TASKS = "calendar_sync_tasks"
        private const val KEY_DRIVE_AUTO_BACKUP = "drive_auto_backup"
        private const val KEY_LAST_CALENDAR_SYNC = "last_calendar_sync"
        private const val KEY_LAST_DRIVE_BACKUP = "last_drive_backup"
    }
}
