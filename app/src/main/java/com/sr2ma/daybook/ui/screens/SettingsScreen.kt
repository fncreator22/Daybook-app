package com.sr2ma.daybook.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.BuildConfig
import com.sr2ma.daybook.R
import com.sr2ma.daybook.sync.SyncSettingsSection
import com.sr2ma.daybook.sync.SyncViewModel
import com.sr2ma.daybook.whatsapp.WhatsAppSettingsSection
import com.sr2ma.daybook.ui.DaybookUiState
import com.sr2ma.daybook.ui.DaybookViewModel
import com.sr2ma.daybook.ui.components.ConfirmDialog
import com.sr2ma.daybook.ui.components.DaybookCard
import com.sr2ma.daybook.ui.components.SectionHeader

/**
 * Backup, a count of what is stored, and the one destructive action.
 *
 * [onExport] and [onImport] are passed in rather than called on the ViewModel
 * directly because both need the Storage Access Framework, and the launchers for
 * that belong to the activity-scoped composable that hosts this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: DaybookUiState,
    viewModel: DaybookViewModel,
    syncViewModel: SyncViewModel,
    onExport: () -> Unit,
    onImport: (replaceExisting: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var askImportMode by remember { mutableStateOf(false) }
    var askDeleteAll by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) })

        // Export and import both hit the disk through a content provider, which can
        // take a moment on a large file; this is the only feedback until the
        // snackbar reports the outcome.
        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader(
                title = stringResource(R.string.settings_section_backup),
                // SectionHeader carries no inset of its own, so a heading that
                // should line up with the card text asks for one here.
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ActionRow(
                icon = R.drawable.ic_export,
                title = stringResource(R.string.settings_export),
                body = stringResource(R.string.settings_export_body),
                enabled = !state.busy,
                onClick = onExport,
            )
            ActionRow(
                icon = R.drawable.ic_import,
                title = stringResource(R.string.settings_import),
                body = stringResource(R.string.settings_import_body),
                enabled = !state.busy,
                onClick = { askImportMode = true },
            )

            // ── Sync & Backup (Google Calendar + Drive) ────────────────────────
            Spacer(Modifier.height(8.dp))
            SyncSettingsSection(syncViewModel = syncViewModel)

            // ── Phase 7: WhatsApp Notification Reader ─────────────────────────
            WhatsAppSettingsSection()

            SectionHeader(
                title = stringResource(R.string.settings_section_data),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            DaybookCard {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    StatRow(stringResource(R.string.settings_stat_tasks), state.taskCount)
                    StatRow(stringResource(R.string.settings_stat_log_entries), state.logCount)
                    StatRow(stringResource(R.string.settings_stat_meetings), state.meetingCount)
                }
            }
            ActionRow(
                icon = R.drawable.ic_delete,
                title = stringResource(R.string.settings_delete_all),
                body = stringResource(R.string.settings_delete_all_body),
                enabled = !state.busy,
                destructive = true,
                onClick = { askDeleteAll = true },
            )

            SectionHeader(
                title = stringResource(R.string.settings_section_about),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            DaybookCard {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_privacy_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (askImportMode) {
        ImportModeDialog(
            onDismiss = { askImportMode = false },
            onChoose = { replaceExisting ->
                askImportMode = false
                onImport(replaceExisting)
            },
        )
    }

    if (askDeleteAll) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_all_confirm_title),
            body = stringResource(R.string.settings_delete_all_confirm_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                askDeleteAll = false
                viewModel.deleteEverything()
            },
            onDismiss = { askDeleteAll = false },
        )
    }
}

/**
 * Merge or replace, asked before the file picker rather than after.
 *
 * Choosing after picking would mean the user has already committed to a file
 * before being told one of the options wipes everything they have.
 */
@Composable
private fun ImportModeDialog(
    onDismiss: () -> Unit,
    onChoose: (replaceExisting: Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_mode_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_import_merge_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.settings_import_replace_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        // Merge is the confirm slot because it is the one that cannot lose data.
        confirmButton = {
            TextButton(onClick = { onChoose(false) }) {
                Text(stringResource(R.string.settings_import_merge))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(true) }) {
                Text(
                    text = stringResource(R.string.settings_import_replace),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

/** A tappable card: icon, what it does, and one line on what that means. */
@Composable
private fun ActionRow(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    // Passing a null onClick while busy is what makes the row inert: DaybookCard
    // draws no click target at all when there is nothing to call.
    DaybookCard(onClick = if (enabled) onClick else null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (destructive) tint else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** One label and its count, for the stored-data card. */
@Composable
private fun StatRow(label: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
