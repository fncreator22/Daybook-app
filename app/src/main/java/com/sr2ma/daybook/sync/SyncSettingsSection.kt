package com.sr2ma.daybook.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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

        if (showGoogleSheet) {
            val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showGoogleSheet = false },
                sheetState = sheetState,
            ) {
                GoogleSignInSheet(
                    isSignedIn = state.isSignedIn,
                    accountEmail = state.accountEmail,
                    googleAuthConfigured = googleAuthConfigured,
                    busy = state.busy,
                    onSignIn = { syncViewModel.signIn(); showGoogleSheet = false },
                    onSignOut = { syncViewModel.signOut(); showGoogleSheet = false },
                )
            }
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
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                SyncToggleRow(
                    title = "Calendar sync — tasks",
                    subtitle = "Push tasks with due dates to Calendar",
                    checked = state.calendarSyncTasks,
                    enabled = state.isSignedIn,
                    onCheckedChange = { syncViewModel.toggleCalendarSyncTasks() },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
                SyncToggleRow(
                    title = "Drive auto-backup",
                    subtitle = "Daily encrypted backup to Drive",
                    checked = state.driveAutoBackup,
                    enabled = state.isSignedIn,
                    onCheckedChange = { syncViewModel.toggleDriveAutoBackup() },
                )
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
                    text = "How much the voice agent can do automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
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
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
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
            onCheckedChange = { onCheckedChange() },
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
    googleAuthConfigured: Boolean,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Google account",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Used only for Calendar sync and Drive backup. Nothing else is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        if (!googleAuthConfigured) {
            Text(
                text = "OAuth client ID not yet configured. See AGENTS.md — Phase 4 wizard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (isSignedIn && accountEmail != null) {
            Text(
                text = "Signed in as $accountEmail",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = onSignOut,
                enabled = !busy,
            ) {
                Text(
                    text = "Sign out",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            TextButton(
                onClick = onSignIn,
                enabled = googleAuthConfigured && !busy,
            ) {
                Text("Continue with Google")
            }
        }
    }
}

