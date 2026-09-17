package com.sr2ma.daybook.whatsapp

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.dao.WhatsAppDao
import com.sr2ma.daybook.domain.model.WhatsAppMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reads WhatsApp notification text from the Android notification shade.
 *
 * Only activated when the user has:
 *  1. Enabled "WhatsApp Reader" in Settings (stored in SharedPreferences).
 *  2. Granted Notification Access in Android Settings → Apps → Special app access.
 *
 * What we read: the notification title (sender display name) and text (message preview).
 * We do NOT access WhatsApp's database, content providers, or any other private data.
 * All captured data stays on-device in the encrypted SQLCipher database.
 *
 * Android package name for WhatsApp: com.whatsapp
 * Android package name for WhatsApp Business: com.whatsapp.w4b
 */
class WhatsAppListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dao: WhatsAppDao? = null

    override fun onCreate() {
        super.onCreate()
        dao = WhatsAppDao(DaybookDatabase.getInstance(applicationContext))
        Log.d(TAG, "NotificationListenerService started")
    }

    override fun onDestroy() {
        super.onDestroy()
        dao = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Only process WhatsApp or WhatsApp Business notifications.
        if (sbn.packageName !in WHATSAPP_PACKAGES) return

        // Check user opt-in preference.
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_ENABLED, false)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()?.trim() ?: return
        val text  = extras.getCharSequence("android.text")?.toString()?.trim()  ?: return

        // Skip group-summary notifications (they duplicate real messages).
        if (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return

        // Skip empty or very short notifications (delivery receipts, typing indicators).
        if (text.length < 2) return

        val msg = WhatsAppMessage(
            sender     = title,
            message    = text,
            receivedAt = sbn.postTime,
            notificationKey = sbn.key,
        )

        scope.launch {
            dao?.insert(msg)
            Log.d(TAG, "Stored message from: $title")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No action needed — we keep the message log even after notification is dismissed.
    }

    companion object {
        private const val TAG = "WhatsAppListener"
        const val PREFS_NAME = "daybook_whatsapp_prefs"
        const val PREF_ENABLED = "whatsapp_reader_enabled"

        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
        )

        /** Check if the user has granted Notification Listener access. */
        fun isPermissionGranted(context: Context): Boolean {
            val cn = ComponentName(context, WhatsAppListenerService::class.java)
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            return flat.contains(cn.flattenToString())
        }

        /** Opens Android's Notification Access settings screen. */
        fun openPermissionSettings(context: Context) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
