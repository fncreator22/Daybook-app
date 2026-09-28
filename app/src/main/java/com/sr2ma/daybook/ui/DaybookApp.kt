package com.sr2ma.daybook.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val syncState by syncViewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val stateHolder = rememberSaveableStateHolder()

    // ── Onboarding Gate ───────────────────────────────────────────────────────
    if (!syncState.onboardingCompleted) {
        com.sr2ma.daybook.ui.screens.OnboardingScreen(
            syncViewModel = syncViewModel,
            onCompleted = { name ->
                syncViewModel.completeOnboarding(name)
            },
        )
        return
    }

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
    // Key ONLY on isListening — retry is handled inside the coroutine with a pause.
    // Keying on voiceRetried too caused a double-fire: the recognizer emitted
    // ERROR_NO_MATCH immediately, voiceRetried flipped, the effect restarted,
    // the second attempt also failed instantly, and the sheet closed before the
    // user could speak.
    LaunchedEffect(state.isListening) {
        if (!state.isListening) return@LaunchedEffect
        var attempts = 0
        var keepGoing = true
        while (keepGoing && attempts < 2) {
            var shouldRetry = false
            voiceCaptureManager.listen().collect { result ->
                when (result) {
                    is VoiceCaptureManager.VoiceResult.Success ->
                        viewModel.onVoiceResult(result.text)
                    is VoiceCaptureManager.VoiceResult.NoMatch,
                    is VoiceCaptureManager.VoiceResult.Unavailable -> {
                        if (attempts == 0) {
                            // First failure: show message and retry after a short pause.
                            viewModel.setRetryMessage()
                            shouldRetry = true
                        } else {
                            // Second failure: give up and show snackbar.
                            viewModel.onVoiceNoMatch()
                            keepGoing = false
                        }
                    }
                    is VoiceCaptureManager.VoiceResult.Error -> {
                        viewModel.onVoiceError((result as VoiceCaptureManager.VoiceResult.Error).code)
                        keepGoing = false
                    }
                }
            }
            attempts++
            if (shouldRetry && keepGoing) kotlinx.coroutines.delay(600L)
        }
    }

    // Listening overlay — shown while mic is active, with a Stop button.
    if (state.isListening) {
        ListeningSheet(message = state.voiceRetryMessage, onStop = viewModel::onVoiceStop)
    }

    if (state.voiceResult != null) {
        VoiceResultSheet(
            result = state.voiceResult!!,
            onConfirm = viewModel::confirmVoiceResult,
            onAddTask = {
                viewModel.commitParsedIntent(
                    state.voiceResult!!.spokenText,
                    state.voiceResult!!.parseResult.copy(intent = ParsedIntent.CREATE_TASK, taskTitle = state.voiceResult!!.spokenText),
                )
                viewModel.dismissVoiceResult()
            },
            onAddMeeting = {
                viewModel.commitParsedIntent(
                    state.voiceResult!!.spokenText,
                    state.voiceResult!!.parseResult.copy(intent = ParsedIntent.CREATE_MEETING, meetingTitle = state.voiceResult!!.spokenText),
                )
                viewModel.dismissVoiceResult()
            },
            onAddLog = {
                viewModel.commitParsedIntent(
                    state.voiceResult!!.spokenText,
                    state.voiceResult!!.parseResult.copy(intent = ParsedIntent.CREATE_LOG, logBody = state.voiceResult!!.spokenText),
                )
                viewModel.dismissVoiceResult()
            },
            onChatWithAgent = {
                val text = state.voiceResult!!.spokenText
                viewModel.dismissVoiceResult()
                viewModel.openConversation()
                viewModel.sendConversationMessage(text)
            },
            onDismiss = viewModel::dismissVoiceResult,
        )
    }

    // Multi-turn conversation sheet — opened by long-pressing the mic FAB.
    if (state.conversationOpen) {
        com.sr2ma.daybook.ui.screens.ConversationSheet(
            messages = state.conversationMessages,
            agentThinking = state.agentThinking,
            onSend = viewModel::sendConversationMessage,
            onSuggestionTap = viewModel::onConversationSuggestion,
            onDismiss = viewModel::closeConversation,
            onVoiceTap = viewModel::startListening,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackHandler(enabled = state.tab != DaybookTab.TODAY) {
            viewModel.selectTab(DaybookTab.TODAY)
        }

        Scaffold(
            topBar = {
                if (state.tab == DaybookTab.WALLET || state.tab == DaybookTab.SETTINGS) {
                    DaybookTopAppBar(
                        currentTab = state.tab,
                        userName = syncState.userName,
                        onAvatarClick = {
                            if (state.tab == DaybookTab.SETTINGS) {
                                viewModel.selectTab(DaybookTab.TODAY)
                            } else {
                                viewModel.selectTab(DaybookTab.SETTINGS)
                            }
                        },
                        onBackClick = { viewModel.selectTab(DaybookTab.TODAY) },
                    )
                }
            },
            bottomBar = { DaybookNavigationBar(selected = state.tab, onSelect = viewModel::selectTab) },
            floatingActionButton = {
                UnifiedExpandablePillFab(
                    tab = state.tab,
                    isListening = state.isListening,
                    onVoiceTap = ::launchMic,
                    onVoiceLongPress = viewModel::openConversation,
                    viewModel = viewModel,
                )
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
                        DaybookTab.TODAY -> TodayScreen(
                            state = state,
                            viewModel = viewModel,
                            userName = syncState.userName,
                            isOnline = syncState.isOnline,
                            connectionType = syncState.connectionType,
                            lastAccessFormatted = syncState.lastAccessFormatted,
                            modifier = content,
                        )
                        DaybookTab.TASKS -> TasksScreen(state, viewModel, content)
                        DaybookTab.LOG -> LogScreen(state, viewModel, content)
                        DaybookTab.MEETINGS -> MeetingsScreen(state, viewModel, content)
                        DaybookTab.WALLET -> WalletScreen(
                            state = state,
                            viewModel = viewModel,
                            userName = syncState.userName,
                            modifier = content,
                        )
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

private val BOTTOM_NAV_TABS = listOf(
    DaybookTab.TODAY,
    DaybookTab.TASKS,
    DaybookTab.MEETINGS,
    DaybookTab.LOG,
)

/**
 * Top app bar with screen title, contextual back navigation for Wallet and Settings,
 * and an avatar profile icon button to navigate to Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaybookTopAppBar(
    currentTab: DaybookTab,
    userName: String?,
    onAvatarClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    TopAppBar(
        title = {
            val title = when (currentTab) {
                DaybookTab.TODAY -> "Daybook"
                DaybookTab.TASKS -> stringResource(R.string.nav_tasks)
                DaybookTab.MEETINGS -> stringResource(R.string.nav_meetings)
                DaybookTab.LOG -> stringResource(R.string.nav_log)
                DaybookTab.WALLET -> "Wallet & Passes"
                DaybookTab.SETTINGS -> stringResource(R.string.nav_settings)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            if (currentTab == DaybookTab.WALLET || currentTab == DaybookTab.SETTINGS) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Back to Today",
                    )
                }
            }
        },
        actions = {
            val avatarInitial = userName?.firstOrNull { it.isLetter() }?.uppercase() ?: ""
            Surface(
                onClick = onAvatarClick,
                shape = CircleShape,
                color = if (currentTab == DaybookTab.SETTINGS) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (avatarInitial.isNotBlank()) {
                        Text(
                            text = avatarInitial,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (currentTab == DaybookTab.SETTINGS) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.nav_settings),
                            tint = if (currentTab == DaybookTab.SETTINGS) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    )
}

/** The 4 destinations in the bottom bar, compliant with Material 3 touch target standards. */
@Composable
private fun DaybookNavigationBar(
    selected: DaybookTab,
    onSelect: (DaybookTab) -> Unit,
) {
    NavigationBar {
        BOTTOM_NAV_TABS.forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                    )
                },
                label = { Text(label) },
            )
        }
    }
}

/**
 * Unified contextual action pill FAB with expandable speed dial and integrated voice agent.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedExpandablePillFab(
    tab: DaybookTab,
    isListening: Boolean,
    onVoiceTap: () -> Unit,
    onVoiceLongPress: () -> Unit,
    viewModel: DaybookViewModel,
) {
    if (tab == DaybookTab.SETTINGS) return

    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var showWalletAddSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.onDocumentSelected(context.applicationContext, uri)
    }

    if (showWalletAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showWalletAddSheet = false },
            sheetState = sheetState,
        ) {
            com.sr2ma.daybook.ui.screens.WalletAddSheet(
                onScan = { showWalletAddSheet = false; viewModel.openWalletScanner() },
                onUploadDocument = { showWalletAddSheet = false; docLauncher.launch("*/*") },
                onManual = { category -> showWalletAddSheet = false; viewModel.newPassManual(category) },
            )
        }
    }

    val primaryAction: () -> Unit = when (tab) {
        DaybookTab.TODAY -> ({ isExpanded = !isExpanded })
        DaybookTab.TASKS -> ({ viewModel.newTask() })
        DaybookTab.MEETINGS -> ({ viewModel.newMeeting() })
        DaybookTab.LOG -> ({ viewModel.newLogEntry() })
        DaybookTab.WALLET -> ({ showWalletAddSheet = true })
        DaybookTab.SETTINGS -> ({})
    }

    val primaryLabel = when (tab) {
        DaybookTab.TODAY -> "Actions"
        DaybookTab.TASKS -> "Task"
        DaybookTab.MEETINGS -> "Meeting"
        DaybookTab.LOG -> "Log"
        DaybookTab.WALLET -> "Pass"
        DaybookTab.SETTINGS -> ""
    }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Expanded action pills
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { it / 2 },
            exit = androidx.compose.animation.fadeOut(tween(150)) +
                androidx.compose.animation.slideOutVertically { it / 2 },
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionMenuItem(
                    iconRes = R.drawable.ic_mic,
                    label = "Assistant Chat",
                    onClick = {
                        isExpanded = false
                        viewModel.openConversation()
                    },
                )
                ActionMenuItem(
                    iconRes = R.drawable.ic_log,
                    label = "Log Entry",
                    onClick = {
                        isExpanded = false
                        viewModel.newLogEntry()
                    },
                )
                ActionMenuItem(
                    iconRes = R.drawable.ic_meetings,
                    label = "New Meeting",
                    onClick = {
                        isExpanded = false
                        viewModel.newMeeting()
                    },
                )
                ActionMenuItem(
                    iconRes = R.drawable.ic_tasks,
                    label = "New Task",
                    onClick = {
                        isExpanded = false
                        viewModel.newTask()
                    },
                )
                ActionMenuItem(
                    iconRes = R.drawable.ic_wallet,
                    label = "Add Pass",
                    onClick = {
                        isExpanded = false
                        showWalletAddSheet = true
                    },
                )
            }
        }

        // Unified Pill FAB
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                // Contextual Primary Action Button
                Row(
                    modifier = Modifier
                        .clickable(onClick = primaryAction)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(if (isExpanded) R.drawable.ic_close else R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = if (isExpanded) "Close" else primaryLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                // Vertical Separator
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                // Voice Mic Action
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(start = 2.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .combinedClickable(
                                onClick = onVoiceTap,
                                onLongClick = {
                                    haptic.performHapticFeedback(
                                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                    )
                                    onVoiceLongPress()
                                },
                                onLongClickLabel = "Open conversation",
                            ),
                        shape = CircleShape,
                        color = if (isListening) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mic),
                                contentDescription = stringResource(R.string.cd_voice_agent),
                                tint = if (isListening) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionMenuItem(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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
    onAddTask: () -> Unit,
    onAddMeeting: () -> Unit,
    onAddLog: () -> Unit,
    onChatWithAgent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parsed = result.parseResult

    val isRecognized = parsed.intent != ParsedIntent.UNKNOWN
    val intentLabel = when (parsed.intent) {
        ParsedIntent.CREATE_TASK ->
            stringResource(R.string.voice_intent_task, parsed.taskTitle ?: result.spokenText)
        ParsedIntent.CREATE_LOG ->
            stringResource(R.string.voice_intent_log, parsed.logBody ?: result.spokenText)
        ParsedIntent.CREATE_MEETING ->
            stringResource(R.string.voice_intent_meeting, parsed.meetingTitle ?: result.spokenText)
        ParsedIntent.CONVERSATION ->
            parsed.conversationReply ?: result.spokenText
        ParsedIntent.QUERY_SCHEDULE ->
            parsed.conversationReply ?: "Today's Agenda & Schedule"
        ParsedIntent.UNKNOWN ->
            "Pick how you want to save or process this:"
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
                text = if (parsed.intent == ParsedIntent.CONVERSATION || parsed.intent == ParsedIntent.QUERY_SCHEDULE) "Daybook Assistant" else if (isRecognized) "Confirm Action" else "Choose Action",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.voice_result_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            // ── Parsed metadata chips ──────────────────────────────────────
            val detailLines = buildList {
                parsed.dueDate?.let { date ->
                    add("\uD83D\uDCC5 ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}")
                }
                if (parsed.priority != com.sr2ma.daybook.domain.model.Priority.MEDIUM) {
                    val badge = when (parsed.priority) {
                        com.sr2ma.daybook.domain.model.Priority.URGENT -> "\uD83D\uDD34 Urgent"
                        com.sr2ma.daybook.domain.model.Priority.HIGH   -> "\uD83D\uDFE0 High priority"
                        com.sr2ma.daybook.domain.model.Priority.LOW    -> "\u26AA Low priority"
                        else -> null
                    }
                    if (badge != null) add(badge)
                }
                if (parsed.meetingAttendees.isNotEmpty()) {
                    add("\uD83D\uDC65 With: ${parsed.meetingAttendees.joinToString(", ")}")
                }
            }
            if (detailLines.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                detailLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isRecognized) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.voice_result_dismiss))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.voice_result_confirm))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onAddTask,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("+ Task", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onAddMeeting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("+ Meeting", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onAddLog,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("+ Log", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss")
                    }
                    Button(onClick = onChatWithAgent) {
                        Text("Ask Assistant")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
