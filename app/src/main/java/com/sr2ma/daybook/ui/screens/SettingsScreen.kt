package com.sr2ma.daybook.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    var askClearMemory by remember { mutableStateOf(false) }
    var askResetPrefs by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showHfTokenDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val syncState by syncViewModel.state.collectAsState()

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importModelFile(uri, context.contentResolver)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        )

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
            // ── Profile ────────────────────────────────────────────────────────
            SectionHeader(
                title = stringResource(R.string.settings_profile_section),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            DaybookCard(onClick = { showProfileSheet = true }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val initial = (syncState.userName?.take(1) ?: syncState.accountEmail?.take(1) ?: "D").uppercase()
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = syncState.userName ?: "Offline User",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val emailText = syncState.accountEmail ?: syncState.profilePrimaryEmail ?: "Offline Profile"
                        val isVerified = syncViewModel.syncManager.syncPrefs.isEmailVerified(emailText)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Text(
                                text = emailText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (syncState.accountEmail != null || syncState.profilePrimaryEmail != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isVerified) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Text(
                                        text = if (isVerified) "VERIFIED" else "UNVERIFIED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isVerified) Color(0xFF137333) else MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showProfileSheet = true }) {
                        Text("Manage")
                    }
                }
            }

            SectionHeader(
                title = stringResource(R.string.settings_section_backup),
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

            // ── On-device AI (Stage 5) ──────────────────────────────────────
            SectionHeader(
                title = stringResource(R.string.settings_section_ai),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            DaybookCard {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = if (state.llmModelReady)
                            stringResource(R.string.settings_ai_model_status_ready)
                        else
                            stringResource(R.string.settings_ai_model_status_missing),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_ai_model_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_ai_attribution),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Model download UI — only shown when model is absent
            if (!state.llmModelReady) {
                val progress = state.modelDownloadProgress
                if (progress != null) {
                    // Downloading — show progress bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_ai_downloading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else {
                    ActionRow(
                        icon = R.drawable.ic_import,
                        title = stringResource(R.string.settings_ai_download_model),
                        body = stringResource(R.string.settings_ai_download_model_body),
                        enabled = !state.busy,
                        onClick = {
                            val token = syncViewModel.syncManager.syncPrefs.huggingFaceToken
                            val customUrl = syncViewModel.syncManager.syncPrefs.customModelUrl
                            viewModel.downloadModel(token = token, customUrl = customUrl)
                        },
                    )
                    ActionRow(
                        icon = R.drawable.ic_settings,
                        title = "Model Download Settings & Token",
                        body = if (syncViewModel.syncManager.syncPrefs.huggingFaceToken != null || syncViewModel.syncManager.syncPrefs.customModelUrl != null)
                            "Configured (HF token or custom mirror set)"
                        else
                            "Optional — tap to enter HF token or direct mirror URL",
                        enabled = !state.busy,
                        onClick = { showHfTokenDialog = true },
                    )
                    ActionRow(
                        icon = R.drawable.ic_import,
                        title = stringResource(R.string.settings_ai_import_model),
                        body = stringResource(R.string.settings_ai_import_model_body),
                        enabled = !state.busy,
                        onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                    )
                }

                // Surface download / import errors in an informative card with Dismiss button
                val downloadError = state.modelDownloadError
                if (downloadError != null) {
                    Spacer(Modifier.height(8.dp))
                    DaybookCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                        ) {
                            Text(
                                text = "Model Notice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = downloadError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (downloadError.contains("401") || downloadError.contains("Hugging Face", ignoreCase = true)) {
                                    TextButton(onClick = { showHfTokenDialog = true }) {
                                        Text("Set HF Token")
                                    }
                                }
                                TextButton(onClick = { viewModel.dismissModelDownloadError() }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }

            ActionRow(
                icon = R.drawable.ic_delete,
                title = stringResource(R.string.settings_ai_clear_memory),
                body = stringResource(R.string.settings_ai_clear_memory_body),
                enabled = !state.busy,
                onClick = { askClearMemory = true },
            )
            ActionRow(
                icon = R.drawable.ic_delete,
                title = stringResource(R.string.settings_ai_reset_prefs),
                body = stringResource(R.string.settings_ai_reset_prefs_body),
                enabled = !state.busy,
                onClick = { askResetPrefs = true },
            )

            SectionHeader(
                title = stringResource(R.string.settings_section_data),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            DaybookCard {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    StatRow(stringResource(R.string.settings_stat_tasks), state.taskCount)
                    StatRow(stringResource(R.string.settings_stat_log_entries), state.logCount)
                    StatRow(stringResource(R.string.settings_stat_meetings), state.meetingCount)
                    StatRow(stringResource(R.string.settings_stat_passes), state.passes.size)
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

    if (askClearMemory) {
        ConfirmDialog(
            title = stringResource(R.string.settings_ai_clear_memory_confirm_title),
            body = stringResource(R.string.settings_ai_clear_memory_confirm_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                askClearMemory = false
                viewModel.clearConversationHistory()
            },
            onDismiss = { askClearMemory = false },
        )
    }

    if (askResetPrefs) {
        ConfirmDialog(
            title = stringResource(R.string.settings_ai_reset_prefs_confirm_title),
            body = stringResource(R.string.settings_ai_reset_prefs_confirm_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = false,
            onConfirm = {
                askResetPrefs = false
                viewModel.resetLearnedPreferences()
            },
            onDismiss = { askResetPrefs = false },
        )
    }

    if (showEditNameDialog) {
        var tempName by remember { mutableStateOf(syncState.userName ?: "") }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(stringResource(R.string.settings_profile_edit_name)) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text(stringResource(R.string.onboarding_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    syncViewModel.setUserName(tempName)
                    showEditNameDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showProfileSheet) {
        ProfileSheet(
            syncPrefs = syncViewModel.syncManager.syncPrefs,
            onProfileUpdated = { syncViewModel.refreshState() },
            onDismiss = { showProfileSheet = false },
        )
    }

    if (showHfTokenDialog) {
        var tokenInput by remember {
            mutableStateOf(syncViewModel.syncManager.syncPrefs.huggingFaceToken ?: "")
        }
        var urlInput by remember {
            mutableStateOf(syncViewModel.syncManager.syncPrefs.customModelUrl ?: "")
        }
        AlertDialog(
            onDismissRequest = { showHfTokenDialog = false },
            title = { Text("Model Download Configuration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Gemma 3 270M is gated on Hugging Face. You can enter your free HF token or supply a custom mirror / CDN URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Hugging Face Token (hf_...)") },
                        placeholder = { Text("hf_xxxxxxxxxxxxxxxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Custom Mirror URL (optional)") },
                        placeholder = { Text("https://mirror.example.com/gemma3.litertlm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleanToken = tokenInput.trim().ifBlank { null }
                    val cleanUrl = urlInput.trim().ifBlank { null }
                    syncViewModel.syncManager.syncPrefs.huggingFaceToken = cleanToken
                    syncViewModel.syncManager.syncPrefs.customModelUrl = cleanUrl
                    showHfTokenDialog = false
                }) {
                    Text("Save Settings")
                }
            },
            dismissButton = {
                Row {
                    if (!syncViewModel.syncManager.syncPrefs.huggingFaceToken.isNullOrBlank() ||
                        !syncViewModel.syncManager.syncPrefs.customModelUrl.isNullOrBlank()) {
                        TextButton(onClick = {
                            syncViewModel.syncManager.syncPrefs.huggingFaceToken = null
                            syncViewModel.syncManager.syncPrefs.customModelUrl = null
                            tokenInput = ""
                            urlInput = ""
                            showHfTokenDialog = false
                        }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showHfTokenDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
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
