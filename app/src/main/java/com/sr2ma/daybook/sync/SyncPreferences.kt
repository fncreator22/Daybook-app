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
class SyncPreferences(
    context: Context? = null,
    customPrefs: android.content.SharedPreferences? = null,
) {

    private val prefs by lazy {
        customPrefs ?: run {
            val ctx = requireNotNull(context) { "Context is required when customPrefs is not provided" }
            val masterKey = MasterKey.Builder(ctx.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    // ── Account & User Profile ───────────────────────────────────────────────

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var profileFirstName: String?
        get() = prefs.getString(KEY_PROFILE_FIRST_NAME, null)
        set(value) = prefs.edit().putString(KEY_PROFILE_FIRST_NAME, value).apply()

    var profileLastName: String?
        get() = prefs.getString(KEY_PROFILE_LAST_NAME, null)
        set(value) = prefs.edit().putString(KEY_PROFILE_LAST_NAME, value).apply()

    var profilePhoneNumber: String?
        get() = prefs.getString(KEY_PROFILE_PHONE_NUMBER, null)
        set(value) = prefs.edit().putString(KEY_PROFILE_PHONE_NUMBER, value).apply()

    var profilePrimaryEmail: String?
        get() = prefs.getString(KEY_PROFILE_PRIMARY_EMAIL, null) ?: accountEmail
        set(value) {
            prefs.edit().putString(KEY_PROFILE_PRIMARY_EMAIL, value).apply()
            if (accountEmail == null && value != null) {
                accountEmail = value
            }
        }

    var profileSubEmails: Set<String>
        get() = prefs.getStringSet(KEY_PROFILE_SUB_EMAILS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_PROFILE_SUB_EMAILS, value).apply()

    var verifiedEmails: Set<String>
        get() = prefs.getStringSet(KEY_VERIFIED_EMAILS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_VERIFIED_EMAILS, value).apply()

    fun addSubEmail(email: String) {
        val trimmed = email.trim().lowercase()
        if (trimmed.isNotEmpty()) {
            profileSubEmails = profileSubEmails + trimmed
        }
    }

    fun removeSubEmail(email: String) {
        val trimmed = email.trim().lowercase()
        profileSubEmails = profileSubEmails - trimmed
        verifiedEmails = verifiedEmails - trimmed
    }

    fun generateOtp(email: String): String {
        val normalized = email.trim().lowercase()
        val code = (100000..999999).random().toString()
        prefs.edit()
            .putString("otp_code_$normalized", code)
            .putLong("otp_time_$normalized", System.currentTimeMillis())
            .apply()
        return code
    }

    fun verifyOtp(email: String, enteredOtp: String): Boolean {
        val normalized = email.trim().lowercase()
        val storedCode = prefs.getString("otp_code_$normalized", null)
        val storedTime = prefs.getLong("otp_time_$normalized", 0L)
        val isValid = storedCode != null &&
            storedCode == enteredOtp.trim() &&
            (System.currentTimeMillis() - storedTime) < 15 * 60 * 1000L
        if (isValid) {
            verifiedEmails = verifiedEmails + normalized
            prefs.edit()
                .remove("otp_code_$normalized")
                .remove("otp_time_$normalized")
                .apply()
            // If primary email was verified, provision offline access token if needed
            if (accountEmail == null || accountEmail == normalized) {
                accountEmail = normalized
                if (accessToken == null) {
                    accessToken = "offline_verified_${System.currentTimeMillis()}"
                    tokenExpiry = Long.MAX_VALUE
                }
            }
        }
        return isValid
    }

    fun isEmailVerified(email: String): Boolean =
        verifiedEmails.contains(email.trim().lowercase())

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

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

    // ── Model Download & Hugging Face Token ───────────────────────────────────

    var huggingFaceToken: String?
        get() = prefs.getString(KEY_HF_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_HF_TOKEN, value).apply()

    var customModelUrl: String?
        get() = prefs.getString(KEY_CUSTOM_MODEL_URL, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_MODEL_URL, value).apply()

    // ── Global Autonomy Guardrail Mode ────────────────────────────────────────
    // ALWAYS_ASK, HYBRID, FULL_AUTONOMY

    var globalAutonomyGuardrail: String
        get() = prefs.getString(KEY_GLOBAL_AUTONOMY, "ALWAYS_ASK") ?: "ALWAYS_ASK"
        set(value) = prefs.edit().putString(KEY_GLOBAL_AUTONOMY, value).apply()

    // ── Network Access Tracking ───────────────────────────────────────────────

    var lastNetworkAccessAt: Long
        get() = prefs.getLong(KEY_LAST_NETWORK_ACCESS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_NETWORK_ACCESS, value).apply()

    fun recordNetworkAccess() {
        lastNetworkAccessAt = System.currentTimeMillis()
    }

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

    var gmailSync: Boolean
        get() = prefs.getBoolean(KEY_GMAIL_SYNC, false)
        set(value) = prefs.edit().putBoolean(KEY_GMAIL_SYNC, value).apply()

    var gmailFilterSpam: Boolean
        get() = prefs.getBoolean(KEY_GMAIL_FILTER_SPAM, true)
        set(value) = prefs.edit().putBoolean(KEY_GMAIL_FILTER_SPAM, value).apply()

    var gmailFilterMarketing: Boolean
        get() = prefs.getBoolean(KEY_GMAIL_FILTER_MARKETING, true)
        set(value) = prefs.edit().putBoolean(KEY_GMAIL_FILTER_MARKETING, value).apply()

    // ── Last-sync timestamps ──────────────────────────────────────────────────

    var lastCalendarSyncAt: Long
        get() = prefs.getLong(KEY_LAST_CALENDAR_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CALENDAR_SYNC, value).apply()

    var lastDriveBackupAt: Long
        get() = prefs.getLong(KEY_LAST_DRIVE_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_DRIVE_BACKUP, value).apply()

    var lastGmailSyncAt: Long
        get() = prefs.getLong(KEY_LAST_GMAIL_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_GMAIL_SYNC, value).apply()

    var sampleGmailCleared: Boolean
        get() = prefs.getBoolean(KEY_SAMPLE_GMAIL_CLEARED, false)
        set(value) = prefs.edit().putBoolean(KEY_SAMPLE_GMAIL_CLEARED, value).apply()

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

    /** Convenience: is a user currently signed in or verified offline? */
    val isSignedIn: Boolean
        get() = (!accountEmail.isNullOrBlank() && !accessToken.isNullOrBlank()) ||
            (!profilePrimaryEmail.isNullOrBlank() && isEmailVerified(profilePrimaryEmail!!)) ||
            verifiedEmails.isNotEmpty()

    // ── Per-tool autonomy levels (§8) — default ASK_EVERY_TIME ───────────────

    fun getAutonomy(category: com.sr2ma.daybook.domain.model.ToolCategory): com.sr2ma.daybook.domain.model.AutonomyLevel =
        com.sr2ma.daybook.domain.model.AutonomyLevel.fromStored(
            prefs.getString("autonomy_${category.storedValue}", null)
        )

    fun setAutonomy(category: com.sr2ma.daybook.domain.model.ToolCategory, level: com.sr2ma.daybook.domain.model.AutonomyLevel) {
        prefs.edit().putString("autonomy_${category.storedValue}", level.storedValue).apply()
    }

    companion object {
        private const val PREFS_NAME = "daybook_sync_prefs"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_PROFILE_FIRST_NAME = "profile_first_name"
        private const val KEY_PROFILE_LAST_NAME = "profile_last_name"
        private const val KEY_PROFILE_PHONE_NUMBER = "profile_phone_number"
        private const val KEY_PROFILE_PRIMARY_EMAIL = "profile_primary_email"
        private const val KEY_PROFILE_SUB_EMAILS = "profile_sub_emails"
        private const val KEY_VERIFIED_EMAILS = "verified_emails"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_HF_TOKEN = "huggingface_token"
        private const val KEY_CUSTOM_MODEL_URL = "custom_model_url"
        private const val KEY_GLOBAL_AUTONOMY = "global_autonomy_guardrail"
        private const val KEY_LAST_NETWORK_ACCESS = "last_network_access"
        private const val KEY_CALENDAR_SYNC_MEETINGS = "calendar_sync_meetings"
        private const val KEY_CALENDAR_SYNC_TASKS = "calendar_sync_tasks"
        private const val KEY_DRIVE_AUTO_BACKUP = "drive_auto_backup"
        private const val KEY_LAST_CALENDAR_SYNC = "last_calendar_sync"
        private const val KEY_LAST_DRIVE_BACKUP = "last_drive_backup"
        private const val KEY_GMAIL_SYNC = "gmail_sync"
        private const val KEY_GMAIL_FILTER_SPAM = "gmail_filter_spam"
        private const val KEY_GMAIL_FILTER_MARKETING = "gmail_filter_marketing"
        private const val KEY_LAST_GMAIL_SYNC = "last_gmail_sync"
        private const val KEY_SAMPLE_GMAIL_CLEARED = "sample_gmail_cleared"
    }
}

