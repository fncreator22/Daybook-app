package com.sr2ma.daybook.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sr2ma.daybook.R
import com.sr2ma.daybook.data.BackupCodec
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
fun DaybookApp(viewModel: DaybookViewModel) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = { DaybookNavigationBar(selected = state.tab, onSelect = viewModel::selectTab) },
            floatingActionButton = { AddButton(tab = state.tab, viewModel = viewModel) },
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
 * One button that means different things beats four buttons, because the thing
 * you want to add is almost always the thing you are already looking at. Settings
 * has nothing to add, so on that tab there is no button at all.
 */
@Composable
private fun AddButton(tab: DaybookTab, viewModel: DaybookViewModel) {
    // Each branch is parenthesised because bare braces after `->` would be read as
    // a block, whose value is Unit, rather than as the lambda this needs.
    val action: () -> Unit = when (tab) {
        DaybookTab.TODAY -> ({ viewModel.newTask(dueToday = true) })
        DaybookTab.TASKS -> ({ viewModel.newTask() })
        DaybookTab.LOG -> ({ viewModel.newLogEntry() })
        DaybookTab.MEETINGS -> ({ viewModel.newMeeting() })
        DaybookTab.WALLET -> ({ viewModel.openWalletScanner() })
        DaybookTab.SETTINGS -> null
    } ?: return

    @StringRes val description = when (tab) {
        DaybookTab.LOG -> R.string.cd_add_log_entry
        DaybookTab.MEETINGS -> R.string.cd_add_meeting
        DaybookTab.WALLET -> R.string.cd_add_pass
        else -> R.string.cd_add_task
    }

    FloatingActionButton(onClick = action) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(description),
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
