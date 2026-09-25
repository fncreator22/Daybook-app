package com.sr2ma.daybook.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.domain.model.AutonomyLevel
import com.sr2ma.daybook.domain.model.ToolCategory
import com.sr2ma.daybook.ui.components.DaybookCard
import com.sr2ma.daybook.ui.components.SectionHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Composable section for Settings tab — "Sync & Backup" row group.
 *
 * Kept in the sync package so that DaybookApp does not need to know about the
 * SyncViewModel internals; it is inserted into SettingsScreen as a composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsSection(
    syncViewModel: SyncViewModel,
    modifier: Modifier = Modifier,
) {
    val state by syncViewModel.state.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val googleAuthConfigured = !context.resources.getBoolean(com.sr2ma.daybook.R.bool.google_auth_placeholder)

    Column(modifier = modifier) {
        SectionHeader(
            title = "Sync & Backup",
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // ── Google account row ────────────────────────────────────────────────
        var showGoogleSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        DaybookCard(onClick = { showGoogleSheet = true }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google account",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (state.isSignedIn && state.accountEmail != null)
                            state.accountEmail!!
                        else if (!googleAuthConfigured)
                            "Setup required (see Settings)"
                        else
                            "Not signed in — tap to connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        var pendingFeatureName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        var pendingToggleAction by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null) }

        androidx.compose.runtime.LaunchedEffect(state.isSignedIn) {
            if (state.isSignedIn) showGoogleSheet = false
        }

        if (showGoogleSheet) {
            val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showGoogleSheet = false },
                sheetState = sheetState,
            ) {
                GoogleSignInSheet(
                    isSignedIn = state.isSignedIn,
                    accountEmail = state.accountEmail,
                    profilePrimaryEmail = state.profilePrimaryEmail,
                    googleAuthConfigured = googleAuthConfigured,
                    busy = state.busy,
                    errorMessage = state.errorMessage,
                    onSignIn = { syncViewModel.signIn() },
                    onSignInOffline = { email -> syncViewModel.signInOffline(email); showGoogleSheet = false },
                    onSignOut = { syncViewModel.signOut(); showGoogleSheet = false },
                )
            }
        }

        if (pendingFeatureName != null) {
            AlertDialog(
                onDismissRequest = { pendingFeatureName = null; pendingToggleAction = null },
                title = { Text("Account Connection Required") },
                text = {
                    Text("To turn on \"$pendingFeatureName\", connect your Google account or link your offline profile.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val email = state.profilePrimaryEmail ?: "offline.user@daybook.local"
                            syncViewModel.signInOffline(email)
                            pendingToggleAction?.invoke()
                            pendingFeatureName = null
                            pendingToggleAction = null
                        }
                    ) {
                        Text("Connect Profile (${state.profilePrimaryEmail ?: "Offline User"})")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            pendingFeatureName = null
                            pendingToggleAction = null
                            showGoogleSheet = true
                        }) {
                            Text("Google Sign-In")
                        }
                        TextButton(onClick = {
                            pendingFeatureName = null
                            pendingToggleAction = null
                        }) {
                            Text("Cancel")
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Calendar sync toggles ─────────────────────────────────────────────
        DaybookCard {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SyncToggleRow(
                    title = "Calendar sync — meetings",
                    subtitle = "Push meetings to Google Calendar",
                    checked = state.calendarSyncMeetings,
                    enabled = state.isSignedIn,
                    onCheckedChange = { syncViewModel.toggleCalendarSyncMeetings() },
                    onDisabledClick = {
                        pendingFeatureName = "Calendar sync — meetings"
                        pendingToggleAction = { syncViewModel.toggleCalendarSyncMeetings() }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                SyncToggleRow(
                    title = "Calendar sync — tasks",
                    subtitle = "Push tasks with due dates to Calendar",
                    checked = state.calendarSyncTasks,
                    enabled = state.isSignedIn,
                    onCheckedChange = { syncViewModel.toggleCalendarSyncTasks() },
                    onDisabledClick = {
                        pendingFeatureName = "Calendar sync — tasks"
                        pendingToggleAction = { syncViewModel.toggleCalendarSyncTasks() }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                SyncToggleRow(
                    title = "Drive auto-backup",
                    subtitle = "Daily encrypted backup to Drive",
                    checked = state.driveAutoBackup,
                    enabled = state.isSignedIn,
                    onCheckedChange = { syncViewModel.toggleDriveAutoBackup() },
                    onDisabledClick = {
                        pendingFeatureName = "Drive auto-backup"
                        pendingToggleAction = { syncViewModel.toggleDriveAutoBackup() }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                SyncToggleRow(
                    title = "Gmail sync & action extraction",
                    subtitle = "Reads incoming emails to extract tasks and meetings",
                    checked = state.gmailSync,
                    enabled = state.isSignedIn,
                    onCheckedChange = { syncViewModel.toggleGmailSync() },
                    onDisabledClick = {
                        pendingFeatureName = "Gmail sync & action extraction"
                        pendingToggleAction = { syncViewModel.toggleGmailSync() }
                    },
                )
                if (state.gmailSync) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                    SyncToggleRow(
                        title = "Filter spam emails",
                        subtitle = "Ignore suspicious emails and prize scams",
                        checked = state.gmailFilterSpam,
                        enabled = state.isSignedIn,
                        onCheckedChange = { syncViewModel.toggleGmailFilterSpam() },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                    SyncToggleRow(
                        title = "Filter marketing newsletters",
                        subtitle = "Ignore promotional emails and discounts",
                        checked = state.gmailFilterMarketing,
                        enabled = state.isSignedIn,
                        onCheckedChange = { syncViewModel.toggleGmailFilterMarketing() },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Agent autonomy levels (§8) ────────────────────────────────────────
        DaybookCard {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = "Agent autonomy",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "How much the voice & chat agent can do automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )

                // 3 Guardrails
                Text(
                    text = "AUTONOMY GUARDRAIL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val currentGuard = state.globalAutonomyGuardrail
                    FilterChip(
                        selected = currentGuard == "ALWAYS_ASK",
                        onClick = { syncViewModel.setGlobalAutonomy("ALWAYS_ASK") },
                        label = { Text("Always Ask", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = currentGuard == "HYBRID",
                        onClick = { syncViewModel.setGlobalAutonomy("HYBRID") },
                        label = { Text("Hybrid", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = currentGuard == "FULL_AUTONOMY",
                        onClick = { syncViewModel.setGlobalAutonomy("FULL_AUTONOMY") },
                        label = { Text("Full Autonomy", style = MaterialTheme.typography.labelSmall) },
                    )
                }

                val guardrailDesc = when (state.globalAutonomyGuardrail) {
                    "FULL_AUTONOMY" -> "Full Autonomy: Voice agent directly creates tasks, notes, and meetings without confirmation dialogs."
                    "HYBRID" -> "Hybrid: Voice agent auto-creates safe tasks and log notes; prompts confirmation ('Sir, can I do that?') for meetings and external sync."
                    else -> "Always Ask: Voice agent always asks for confirmation ('Sir, can I do that?') before executing any action."
                }
                Text(
                    text = guardrailDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "TOOL-LEVEL PERMISSIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                ToolCategory.entries.forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    val currentLevel = state.autonomyLevels[category] ?: AutonomyLevel.ASK_EVERY_TIME
                    AutonomyRow(
                        category = category,
                        selected = currentLevel,
                        onSelect = { level -> syncViewModel.setAutonomy(category, level) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Action buttons ────────────────────────────────────────────────────
        DaybookCard(onClick = if (state.isSignedIn && !state.busy) syncViewModel::syncNow else null) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                Text("Sync now", style = MaterialTheme.typography.bodyLarge)
                if (state.lastCalendarSyncAt > 0L) {
                    Text(
                        text = "Last: ${formatTimestamp(state.lastCalendarSyncAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        DaybookCard(onClick = if (state.isSignedIn && !state.busy) syncViewModel::backupNow else null) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                Text("Back up now", style = MaterialTheme.typography.bodyLarge)
                if (state.lastDriveBackupAt > 0L) {
                    Text(
                        text = "Last: ${formatTimestamp(state.lastDriveBackupAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        DaybookCard(onClick = if (state.isSignedIn && !state.busy) syncViewModel::openRestorePicker else null) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                Text("Restore from backup", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Lists backups from your Drive",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.gmailSync) {
            Spacer(Modifier.height(8.dp))
            DaybookCard(onClick = if (state.isSignedIn && !state.busy) syncViewModel::syncGmailNow else null) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                    Text("Sync Gmail now", style = MaterialTheme.typography.bodyLarge)
                    if (state.lastGmailSyncAt > 0L) {
                        Text(
                            text = "Last: ${formatTimestamp(state.lastGmailSyncAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.gmailSyncMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }

    // ── Restore picker dialog ─────────────────────────────────────────────────
    if (state.showRestorePicker) {
        RestorePickerDialog(
            backups = state.driveBackups,
            onDismiss = syncViewModel::dismissRestorePicker,
            onSelect = { fileId ->
                // Restore is handled by the caller (needs db key from AppContainer)
                // For now we dismiss — real restore wired in MainActivity
                syncViewModel.dismissRestorePicker()
            },
        )
    }
}

@Composable
private fun SyncToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: () -> Unit,
    onDisabledClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!enabled && onDisabledClick != null) {
                    Modifier.clickable(onClick = onDisabledClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange() else onDisabledClick?.invoke() },
            enabled = enabled,
        )
    }
}

/**
 * One row of the Agent Autonomy card — the category label on the left and a
 * three-segment chip row (Ask | Session | Always) on the right.
 *
 * Ask       = agent must ask every time (default for off-device writes, §8)
 * Session   = auto-approved for this app session; resets on cold start
 * Always    = permanently auto; user must explicitly opt in
 */
@Composable
private fun AutonomyRow(
    category: ToolCategory,
    selected: AutonomyLevel,
    onSelect: (AutonomyLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(modifier = Modifier.padding(top = 4.dp)) {
            AutonomyLevel.entries.forEach { level ->
                FilterChip(
                    selected = selected == level,
                    onClick = { onSelect(level) },
                    label = { Text(level.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RestorePickerDialog(
    backups: List<DriveBackupWorker.DriveFile>,
    onDismiss: () -> Unit,
    onSelect: (fileId: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from backup") },
        text = {
            Column {
                if (backups.isEmpty()) {
                    Text("No backups found on Drive.")
                } else {
                    backups.forEach { file ->
                        TextButton(
                            onClick = { onSelect(file.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = file.createdTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatTimestamp(ts: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ts))

// ── Google Sign-In Bottom Sheet ───────────────────────────────────────────────

/**
 * ModalBottomSheet content for Google account management.
 * Auth is fully optional — sync features are gated on isSignedIn.
 * The app works completely offline without signing in.
 */
@Composable
fun GoogleSignInSheet(
    isSignedIn: Boolean,
    accountEmail: String?,
    profilePrimaryEmail: String? = null,
    googleAuthConfigured: Boolean,
    busy: Boolean,
    errorMessage: String? = null,
    onSignIn: () -> Unit,
    onSignInOffline: ((String) -> Unit)? = null,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var customEmailText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Google Account & Cloud Sync",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Text(
            text = "Used for Calendar sync, Drive backup, and Gmail action extraction. Daybook operates offline-first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()

        if (isSignedIn && accountEmail != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connected Account",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = accountEmail,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                    }
                    TextButton(
                        onClick = onSignOut,
                        enabled = !busy,
                    ) {
                        Text(
                            text = "Disconnect",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        } else {
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Sign-In Notice",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            // Option 1: Native Google Credential Manager
            androidx.compose.material3.Button(
                onClick = onSignIn,
                enabled = googleAuthConfigured && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Connecting with Google..." else "Continue with Google")
            }

            // Option 2: Connect via Offline Profile Email
            if (!profilePrimaryEmail.isNullOrBlank() && onSignInOffline != null) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { onSignInOffline(profilePrimaryEmail) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Connect Profile Email ($profilePrimaryEmail)")
                }
            }

            // Option 3: Manual / Test account input
            if (onSignInOffline != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Or connect offline/test email address:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = customEmailText,
                        onValueChange = { customEmailText = it },
                        placeholder = { Text("user@gmail.com") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.Button(
                        onClick = {
                            if (customEmailText.isNotBlank()) {
                                onSignInOffline(customEmailText.trim())
                            }
                        },
                        enabled = customEmailText.isNotBlank(),
                    ) {
                        Text("Connect")
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Notice: If Google Sign-In displays 'No credential available' (unregistered SHA1/Client ID in Google Console), tap 'Connect Profile Email' above to enable all sync and autonomous toggles immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

