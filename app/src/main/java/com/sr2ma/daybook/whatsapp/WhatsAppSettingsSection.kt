package com.sr2ma.daybook.whatsapp

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.ui.components.DaybookCard
import com.sr2ma.daybook.ui.components.SectionHeader

/**
 * Settings section for the WhatsApp notification reader feature.
 *
 * This section is shown in the Settings tab. The toggle is OFF by default.
 * When the user turns it on:
 *  1. We show an onboarding dialog explaining exactly what is read and stored.
 *  2. If Notification Access is not yet granted, we take them to Android Settings.
 *  3. Once both are true, messages are captured.
 */
@Composable
fun WhatsAppSettingsSection() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(WhatsAppListenerService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var enabled by remember {
        mutableStateOf(prefs.getBoolean(WhatsAppListenerService.PREF_ENABLED, false))
    }
    var showOnboarding by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    var showRestrictedHelp by remember { mutableStateOf(false) }

    val hasPermission = WhatsAppListenerService.isPermissionGranted(context)

    Spacer(Modifier.height(8.dp))
    SectionHeader(
        title = "WhatsApp Reader",
    )
    DaybookCard {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Capture incoming messages",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val statusText = when {
                        !enabled -> "Off — tap to enable"
                        !hasPermission -> "Permission needed — notification access blocked"
                        else -> "Active — reading WhatsApp notifications"
                    }
                    val statusColor = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        !hasPermission -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { newValue ->
                        if (newValue) {
                            showOnboarding = true
                        } else {
                            prefs.edit().putBoolean(WhatsAppListenerService.PREF_ENABLED, false).apply()
                            enabled = false
                        }
                    },
                )
            }

            if (enabled && !hasPermission) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { showPermissionRationale = true },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(
                        text = "Grant Notification Access / Fix Restricted Settings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { showRestrictedHelp = true },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(
                        text = "Restricted settings issue on Android 13+? Tap for help",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = { showOnboarding = false },
            title = { Text("What WhatsApp Reader does") },
            text = {
                Text(
                    "Daybook will read the sender name and message preview from WhatsApp " +
                    "notifications that appear in your notification shade.\n\n" +
                    "• Only notification text is captured — not your full chat history.\n" +
                    "• All data stays on this device, encrypted.\n" +
                    "• You can reply via voice; Daybook opens WhatsApp pre-filled — you press Send.\n\n" +
                    "Android will ask you to grant Notification Access on the next screen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOnboarding = false
                    prefs.edit().putBoolean(WhatsAppListenerService.PREF_ENABLED, true).apply()
                    enabled = true
                    if (!WhatsAppListenerService.isPermissionGranted(context)) {
                        showPermissionRationale = true
                    }
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showOnboarding = false }) { Text("Cancel") }
            },
        )
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Grant Notification Access") },
            text = {
                Text(
                    "On the next screen: find Daybook in the list and toggle it on. " +
                    "This lets Daybook read notification text from WhatsApp.\n\n" +
                    "Note: If Android shows 'Restricted setting' or blocks the toggle, tap 'Fix Restricted Settings'.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    WhatsAppListenerService.openPermissionSettings(context)
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    showRestrictedHelp = true
                }) { Text("Fix Restricted Settings") }
            },
        )
    }

    if (showRestrictedHelp) {
        AlertDialog(
            onDismissRequest = { showRestrictedHelp = false },
            title = { Text("Allow Restricted Settings (Android 13+)") },
            text = {
                Text(
                    "Android restricts notification access for sideloaded apps by default with the message:\n" +
                    "\"Restricted setting: For your security, this setting is currently unavailable.\"\n\n" +
                    "To allow access:\n" +
                    "1. Tap 'Open App Info' below.\n" +
                    "2. In the top-right corner of the App info page, tap the 3 dots (⋮).\n" +
                    "3. Tap 'Allow restricted settings' and confirm with your PIN/fingerprint.\n" +
                    "4. Return here and tap 'Open Notification Access' to toggle Daybook ON.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", context.packageName, null)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }) { Text("1. Open App Info") }
            },
            dismissButton = {
                TextButton(onClick = {
                    WhatsAppListenerService.openPermissionSettings(context)
                }) { Text("2. Open Notification Access") }
            },
        )
    }
}
