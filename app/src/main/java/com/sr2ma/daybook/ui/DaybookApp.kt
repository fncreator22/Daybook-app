package com.sr2ma.daybook.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.data.BackupCodec
import com.sr2ma.daybook.domain.ParsedIntent
import com.sr2ma.daybook.sync.SyncViewModel
import com.sr2ma.daybook.ui.editors.LogEditor
import com.sr2ma.daybook.ui.editors.MeetingEditor
import com.sr2ma.daybook.ui.editors.PassConfirmSheet
import com.sr2ma.daybook.ui.editors.TaskEditor
import com.sr2ma.daybook.ui.screens.LogScreen
import com.sr2ma.daybook.ui.screens.MeetingsScreen
import com.sr2ma.daybook.ui.screens.SettingsScreen
import com.sr2ma.daybook.ui.screens.TasksScreen
import com.sr2ma.daybook.ui.screens.TodayScreen
import com.sr2ma.daybook.ui.screens.WalletScreen

/**
 * What the import picker will show.
 *
 * A backup is JSON, but document providers disagree about how to type a .json
 * file: some report application/json, some text/plain, and some fall back to
 * application/octet-stream. Listing all three keeps real backups visible in the
 * picker. Whatever is chosen is fully parsed and validated before a single row is
 * written, so picking the wrong file is reported rather than acted on.
 */
private val IMPORT_MIME_TYPES = arrayOf(
    BackupCodec.MIME_TYPE,
    "text/plain",
    "application/octet-stream",
)

/**
 * The whole app: bottom bar, add button, snackbar, screens, editors.
 *
 * Everything that is shared between screens lives here rather than being repeated
 * in each of them, which is why the five screens are plain content composables
 * that take a modifier and nothing else. The document pickers also have to live
 * at this level: a launcher has to be registered before the composition it
 * belongs to is used, and Settings is created and destroyed as tabs change.
 */
@Composable
fun DaybookApp(viewModel: DaybookViewModel, syncViewModel: SyncViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val stateHolder = rememberSaveableStateHolder()

    // Which import mode the picker was opened for. It is held here because the
    // choice is made in a dialog before the picker opens and is needed again in
    // the callback after it closes.
    //
    // rememberSaveable, not remember: the document picker is a separate activity
    // and this one can be destroyed behind it. A plain remember would come back as
    // false, silently turning the Replace the user asked for into a Merge.
    var pendingReplace by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupCodec.MIME_TYPE),
    ) { uri ->
        // A null Uri means the picker was dismissed, which is not a failure and so
        // gets no message.
        if (uri != null) {
            viewModel.exportTo {
                // "wt" for truncate. The default "w" is not required to shorten an
                // existing file, so overwriting a backup with a smaller one can
                // leave the tail of the old JSON behind and corrupt it.
                context.contentResolver.openOutputStream(uri, "wt")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importFrom(pendingReplace) {
                context.contentResolver.openInputStream(uri)
            }
        }
    }

    // The id of the last message actually shown. Without it, rotating the phone
    // while a snackbar is up restarts the effect below and shows the same message
    // a second time; keyed on the id, a repeat of the same text still counts as a
    // new message and is shown again.
    var shownMessageId by rememberSaveable { mutableStateOf(0L) }

    val message = state.message
    LaunchedEffect(message?.id) {
        if (message != null && message.id != shownMessageId) {
            shownMessageId = message.id
            // getString, not stringResource: this is a coroutine rather than a
            // composition, so there is no composition-local scope to read from.
            snackbarHostState.showSnackbar(
                context.getString(message.textRes, *message.args.toTypedArray()),
            )
            viewModel.consumeMessage()
        }
    }

    val syncState by syncViewModel.state.collectAsState()
    val syncErrorMessage = syncState.errorMessage
    LaunchedEffect(syncErrorMessage) {
        if (syncErrorMessage != null) {
            snackbarHostState.showSnackbar(syncErrorMessage)
            syncViewModel.consumeError()
        }
    }

    // ── Voice agent ─────────────────────────────────────────────────────────
    val voiceCaptureManager = remember { VoiceCaptureManager(context) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        } else {
            viewModel.onVoiceError(android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        }
    }

    fun launchMic() {
        val alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.startListening()
        } else {
            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // Collect the voice flow while isListening == true.
    LaunchedEffect(state.isListening, state.voiceRetried) {
        if (state.isListening) {
            voiceCaptureManager.listen().collect { result ->
                when (result) {
                    is VoiceCaptureManager.VoiceResult.Success -> viewModel.onVoiceResult(result.text)
                    is VoiceCaptureManager.VoiceResult.NoMatch -> viewModel.onVoiceNoMatch()
                    is VoiceCaptureManager.VoiceResult.Unavailable -> viewModel.onVoiceNoMatch()
                    is VoiceCaptureManager.VoiceResult.Error -> viewModel.onVoiceError(result.code)
                }
            }
        }
    }

    // Listening overlay — shown while mic is active, with a Stop button.
    if (state.isListening) {
        ListeningSheet(message = state.voiceRetryMessage, onStop = viewModel::onVoiceStop)
    }

    // Confirmation sheet shown after recognition completes.
    if (state.voiceResult != null) {
        VoiceResultSheet(
            result = state.voiceResult!!,
            onConfirm = viewModel::confirmVoiceResult,
            onDismiss = viewModel::dismissVoiceResult,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = { DaybookNavigationBar(selected = state.tab, onSelect = viewModel::selectTab) },
            floatingActionButton = {
                // Two FABs stacked: per-tab add (top) + voice mic (bottom / primary).
                Column(horizontalAlignment = Alignment.End) {
                    AddButton(tab = state.tab, viewModel = viewModel)
                    Spacer(Modifier.height(12.dp))
                    VoiceAgentButton(
                        isListening = state.isListening,
                        onTap = ::launchMic,
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            // The Scaffold's insets are applied once, here, so no screen has to
            // know that there is a bottom bar above it.
            val content = Modifier.padding(padding)

            if (!state.loaded) {
                LoadingGate(modifier = content)
            } else {
                // Leaving a tab removes its screen from the composition entirely, so
                // without this its scroll position and any half-typed quick-add text
                // would be gone on return. The holder keeps each tab's rememberSaveable
                // state -- which is what rememberLazyListState uses -- keyed by tab.
                stateHolder.SaveableStateProvider(state.tab.name) {
                    when (state.tab) {
                        DaybookTab.TODAY -> TodayScreen(state, viewModel, content)
                        DaybookTab.TASKS -> TasksScreen(state, viewModel, content)
                        DaybookTab.LOG -> LogScreen(state, viewModel, content)
                        DaybookTab.MEETINGS -> MeetingsScreen(state, viewModel, content)
                        DaybookTab.WALLET -> WalletScreen(state, viewModel, content)
                        DaybookTab.SETTINGS -> SettingsScreen(
                            state = state,
                            viewModel = viewModel,
                            syncViewModel = syncViewModel,
                            onExport = {
                                exportLauncher.launch(BackupCodec.suggestedFileName(state.today))
                            },
                            onImport = { replaceExisting ->
                                pendingReplace = replaceExisting
                                importLauncher.launch(IMPORT_MIME_TYPES)
                            },
                            modifier = content,
                        )
                    }
                }
            }
        }

        // A sibling of the Scaffold rather than part of its content, so an open
        // editor covers the bottom bar and the add button too. An editor is a
        // screen you are on, not a panel floating over one.
        EditorHost(state = state, viewModel = viewModel)
    }
}

/**
 * Whichever editor the state asks for, or nothing.
 *
 * The meeting title and the action-item list are read off the state rather than
 * computed here, because a composable body runs again on every keystroke in the
 * sheet and neither of them depends on what is being typed. The ViewModel derives
 * both once per data change instead.
 */
@Composable
private fun EditorHost(state: DaybookUiState, viewModel: DaybookViewModel) {
    when (val editor = state.editor) {
        null -> Unit

        is Editor.TaskSheet -> TaskEditor(
            seed = editor.seed,
            today = state.today,
            projects = state.projects,
            meetingTitle = state.editorMeetingTitle,
            onSave = viewModel::saveTask,
            onDelete = { viewModel.deleteTask(editor.seed) },
            onDismiss = viewModel::closeEditor,
        )

        is Editor.LogSheet -> LogEditor(
            seed = editor.seed,
            today = state.today,
            onSave = viewModel::saveLogEntry,
            onDelete = { viewModel.deleteLogEntry(editor.seed) },
            onDismiss = viewModel::closeEditor,
        )

        is Editor.MeetingSheet -> MeetingEditor(
            seed = editor.seed,
            today = state.today,
            actionItems = state.editorActionItems,
            onSave = viewModel::saveMeeting,
            onDelete = { viewModel.deleteMeeting(editor.seed) },
            onDismiss = viewModel::closeEditor,
            onAddActionItem = { title -> viewModel.addActionItem(editor.seed.id, title) },
            onToggleActionItem = viewModel::toggleTaskDone,
        )

        is Editor.PassSheet -> PassConfirmSheet(
            seed = editor.seed,
            onSave = viewModel::savePass,
            onDelete = { viewModel.deletePass(editor.seed) },
            onDismiss = viewModel::closeEditor,
        )
    }
}

/** The six destinations, in the order they are worked through in a day. */
@Composable
private fun DaybookNavigationBar(
    selected: DaybookTab,
    onSelect: (DaybookTab) -> Unit,
) {
    NavigationBar {
        DaybookTab.entries.forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        // The label sits directly underneath, so describing the
                        // icon as well would have a screen reader say it twice.
                        contentDescription = null,
                    )
                },
                label = { Text(label) },
            )
        }
    }
}

/**
 * The add button, which adds whatever the current tab is about.
 *
 * Wallet tab shows a two-option dialog: scan barcode or add manually.
 * Settings has nothing to add, so on that tab there is no button at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddButton(tab: DaybookTab, viewModel: DaybookViewModel) {
    if (tab == DaybookTab.SETTINGS) return

    if (tab == DaybookTab.WALLET) {
        WalletAddMenu(viewModel)
        return
    }

    // Each branch is parenthesised because bare braces after `->` would be read as
    // a block, whose value is Unit, rather than as the lambda this needs.
    val action: () -> Unit = when (tab) {
        DaybookTab.TODAY    -> ({ viewModel.newTask(dueToday = true) })
        DaybookTab.TASKS    -> ({ viewModel.newTask() })
        DaybookTab.LOG      -> ({ viewModel.newLogEntry() })
        DaybookTab.MEETINGS -> ({ viewModel.newMeeting() })
        else -> return // WALLET + SETTINGS already handled above
    }

    @StringRes val description = when (tab) {
        DaybookTab.LOG      -> R.string.cd_add_log_entry
        DaybookTab.MEETINGS -> R.string.cd_add_meeting
        else                -> R.string.cd_add_task
    }

    androidx.compose.material3.SmallFloatingActionButton(onClick = action) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(description),
        )
    }
}

/**
 * Small FAB for the Wallet tab. Tapping it shows a dialog offering
 * "Scan barcode" or "Add manually" so no camera is ever forced.
 */
@Composable
private fun WalletAddMenu(viewModel: DaybookViewModel) {
    var showDialog by remember { mutableStateOf(false) }

    androidx.compose.material3.SmallFloatingActionButton(onClick = { showDialog = true }) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(R.string.cd_add_pass),
        )
    }

    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.cd_add_pass)) },
            text = {
                Column {
                    TextButton(onClick = {
                        showDialog = false
                        viewModel.openWalletScanner()
                    }) { Text(stringResource(R.string.wallet_add_scan)) }
                    TextButton(onClick = {
                        showDialog = false
                        viewModel.newPassManual()
                    }) { Text(stringResource(R.string.wallet_add_manual)) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/**
 * Shown for the one frame or two before the first read of the database returns.
 *
 * Without it the app opens on an empty-state message and then replaces it with
 * content, which reads as "you have nothing" followed by "actually you do".
 */
@Composable
private fun LoadingGate(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Floating mic button. Tapping it starts the voice agent.
 * While listening, the [ListeningSheet] handles the UX — this button
 * just changes colour to indicate active state.
 */
@Composable
private fun VoiceAgentButton(
    isListening: Boolean,
    onTap: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 0f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            )
        }
        FloatingActionButton(
            onClick = onTap,
            containerColor = if (isListening) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = stringResource(R.string.cd_voice_agent),
                tint = if (isListening) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * Full-screen overlay shown while the mic is actively listening.
 *
 * Shows animated wave dots, a "Listening…" label, and a Stop button
 * so the user can cancel at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListeningSheet(message: String?, onStop: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Three dots bounce with staggered timing to suggest a waveform.
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val dot0 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "d0",
    )
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(380, delayMillis = 120), RepeatMode.Reverse),
        label = "d1",
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(380, delayMillis = 240), RepeatMode.Reverse),
        label = "d2",
    )

    val dotColor = MaterialTheme.colorScheme.primary

    ModalBottomSheet(onDismissRequest = onStop, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message ?: stringResource(R.string.voice_listening),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(dot0, dot1, dot2).forEach { yOff ->
                    Canvas(
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { translationY = yOff },
                    ) {
                        drawCircle(color = dotColor)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            TextButton(onClick = onStop) { Text(stringResource(R.string.voice_result_dismiss)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}


/**
 * Bottom sheet shown when a voice recognition result is ready.
 * The user sees what was heard and what action will be taken, then
 * confirms or dismisses without committing to the database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceResultSheet(
    result: VoiceAgentResult,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val intentLabel = when (result.parseResult.intent) {
        ParsedIntent.CREATE_TASK ->
            stringResource(R.string.voice_intent_task, result.parseResult.taskTitle ?: result.spokenText)
        ParsedIntent.CREATE_LOG ->
            stringResource(R.string.voice_intent_log, result.parseResult.logBody ?: result.spokenText)
        ParsedIntent.CREATE_MEETING ->
            stringResource(R.string.voice_intent_meeting, result.parseResult.meetingTitle ?: result.spokenText)
        ParsedIntent.UNKNOWN ->
            stringResource(R.string.voice_intent_task, result.spokenText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.voice_result_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "\"${result.spokenText}\"",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = intentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.voice_result_dismiss))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.voice_result_confirm))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
