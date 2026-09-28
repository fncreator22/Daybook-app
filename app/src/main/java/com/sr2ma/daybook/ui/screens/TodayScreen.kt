package com.sr2ma.daybook.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.AgentSuggestion
import com.sr2ma.daybook.domain.BriefingWriter
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.SuggestionType
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.WhatsAppMessage
import com.sr2ma.daybook.ui.DaybookUiState
import com.sr2ma.daybook.ui.DaybookViewModel
import com.sr2ma.daybook.ui.components.AccentChip
import com.sr2ma.daybook.ui.components.DaybookCard
import com.sr2ma.daybook.ui.components.EmptyState
import com.sr2ma.daybook.ui.components.LogEntryRow
import com.sr2ma.daybook.ui.components.MeetingRow
import com.sr2ma.daybook.ui.components.SectionHeader
import com.sr2ma.daybook.ui.components.TaskRow
import androidx.compose.material3.Button
import androidx.compose.ui.text.style.TextOverflow
import com.sr2ma.daybook.domain.model.GmailMessage
import com.sr2ma.daybook.ui.components.GlassCard
import com.sr2ma.daybook.ui.theme.DaybookAccents
import com.sr2ma.daybook.whatsapp.WhatsAppReplyHelper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.sr2ma.daybook.ui.components.ConnectivityStatusDialog
import com.sr2ma.daybook.ui.screens.EntryPassSheet
import java.time.LocalDate
import kotlinx.coroutines.delay
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import com.sr2ma.daybook.domain.NaturalLanguageParser
import com.sr2ma.daybook.domain.ParsedIntent
import com.sr2ma.daybook.domain.TodayBoard
import com.sr2ma.daybook.sync.GmailSyncEngine
import androidx.compose.material3.OutlinedButton
import com.sr2ma.daybook.ui.components.TimelineItemRow
import com.sr2ma.daybook.ui.components.PlatformBadge
import com.sr2ma.daybook.ui.components.DurationBadge
import com.sr2ma.daybook.ui.components.ActiveStatusDot
import com.sr2ma.daybook.ui.theme.DarkPillBg
import com.sr2ma.daybook.ui.theme.HeroNavyBg
import com.sr2ma.daybook.ui.theme.CadenceNavyBg
import com.sr2ma.daybook.ui.theme.EmeraldTeal
import com.sr2ma.daybook.ui.theme.ElectricBlue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp

/**
 * The landing screen: one scrollable day, bucketed by what it is asking of you.
 *
 * The buckets are computed by `TodayBuilder` and arrive ready in `state.board`, so
 * this file only decides order and wording. It has no app bar, because the date
 * heading says everything a title would and takes the space more usefully.
 */
@Composable
fun TodayScreen(
    state: DaybookUiState,
    viewModel: DaybookViewModel,
    userName: String? = null,
    isOnline: Boolean = false,
    connectionType: String = "Offline",
    lastAccessFormatted: String = "Never (100% Offline)",
    modifier: Modifier = Modifier,
) {
    val board = state.board
    var viewingPass by remember { mutableStateOf<Pass?>(null) }
    var viewingMeeting by remember { mutableStateOf<Meeting?>(null) }
    var viewingTask by remember { mutableStateOf<Task?>(null) }
    var viewingGmailMessage by remember { mutableStateOf<GmailMessage?>(null) }
    var viewingWhatsAppMessage by remember { mutableStateOf<WhatsAppMessage?>(null) }
    var showConnectivityDialog by remember { mutableStateOf(false) }
    var showBriefingSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var dismissedSuggestionKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }

    val visibleGmailMessages = remember(state.recentGmailMessages, state.tasks, state.meetings, state.today) {
        state.recentGmailMessages.filter { msg ->
            if (msg.actionedAt != null) {
                val relatedTask = state.tasks.find { it.notes.contains("[msgId:${msg.messageId}]") }
                if (relatedTask != null && relatedTask.status == com.sr2ma.daybook.domain.model.TaskStatus.DONE) {
                    return@filter false
                }
                val relatedMeeting = state.meetings.find { it.notes.contains("[msgId:${msg.messageId}]") }
                if (relatedMeeting != null && relatedMeeting.day < state.today) {
                    return@filter false
                }
                val dayInMillis = 24 * 60 * 60 * 1000L
                if (System.currentTimeMillis() - msg.actionedAt > dayInMillis) {
                    return@filter false
                }
            } else {
                val (extractedDate, _) = GmailSyncEngine.extractDateTime(msg.subject, msg.snippet, state.today)
                if (extractedDate != null && extractedDate < state.today) {
                    return@filter false
                }
            }
            true
        }
    }

    val activeSuggestions = remember(board.suggestions, dismissedSuggestionKeys) {
        board.suggestions.filter { suggestion ->
            val key = "${suggestion.type}_${suggestion.relatedEntityType}_${suggestion.relatedEntityId}_${suggestion.body}"
            !dismissedSuggestionKeys.contains(key)
        }
    }

    if (showConnectivityDialog) {
        ConnectivityStatusDialog(
            isOnline = isOnline,
            connectionType = connectionType,
            lastAccessFormatted = lastAccessFormatted,
            lastSyncDiff = viewModel.syncPreferences?.lastGmailSyncDiffSummary,
            onDismiss = { showConnectivityDialog = false },
        )
    }

    if (showBriefingSheet) {
        BriefingSheet(
            board = board,
            today = state.today,
            userName = userName,
            onDismiss = { showBriefingSheet = false },
        )
    }

    if (showProfileSheet) {
        val syncPrefs = viewModel.syncPreferences ?: com.sr2ma.daybook.sync.SyncPreferences(LocalContext.current)
        ProfileSheet(
            syncPrefs = syncPrefs,
            taskCount = state.tasks.size,
            meetingCount = state.meetings.size,
            logCount = state.logDays.sumOf { it.entries.size },
            passCount = state.passes.size,
            onProfileUpdated = {},
            onDismiss = { showProfileSheet = false },
        )
    }

    val currentPass = viewingPass?.let { vp -> state.passes.find { it.id == vp.id } ?: vp }
    currentPass?.let { pass ->
        EntryPassSheet(
            pass = pass,
            userName = userName,
            onEdit = { passToEdit ->
                viewingPass = null
                viewModel.editPass(passToEdit)
            },
            onDelete = { passToDelete ->
                viewingPass = null
                viewModel.deletePass(passToDelete)
            },
            onToggleFavorite = { passToFav ->
                viewModel.toggleFavoritePass(passToFav.id)
            },
            onToggleArchive = { passToArchive ->
                viewModel.toggleArchivePass(passToArchive.id)
            },
            onDismiss = { viewingPass = null },
        )
    }

    val currentMeeting = viewingMeeting?.let { vm -> state.meetings.find { it.id == vm.id } ?: vm }
    currentMeeting?.let { meeting ->
        MeetingCardSheet(
            meeting = meeting,
            today = state.today,
            onEdit = { meetingToEdit ->
                viewingMeeting = null
                viewModel.editMeeting(meetingToEdit)
            },
            onToggleFollowUp = { meetingToToggle ->
                viewModel.toggleFollowUpDone(meetingToToggle)
            },
            onDelete = { meetingToDelete ->
                viewingMeeting = null
                viewModel.deleteMeeting(meetingToDelete)
            },
            onDismiss = { viewingMeeting = null },
        )
    }

    val currentTask = viewingTask?.let { vt -> state.tasks.find { it.id == vt.id } ?: vt }
    currentTask?.let { task ->
        TaskCardSheet(
            task = task,
            today = state.today,
            onEdit = { taskToEdit ->
                viewingTask = null
                viewModel.editTask(taskToEdit)
            },
            onToggleDone = { taskToToggle ->
                viewModel.toggleTaskDone(taskToToggle)
            },
            onDelete = { taskToDelete ->
                viewingTask = null
                viewModel.deleteTask(taskToDelete)
            },
            onUpdateTask = { taskToUpdate ->
                viewModel.saveTask(taskToUpdate)
            },
            onDismiss = { viewingTask = null },
        )
    }

    val currentGmail = viewingGmailMessage?.let { gm -> state.recentGmailMessages.find { it.id == gm.id } ?: gm }
    currentGmail?.let { msg ->
        GmailCardSheet(
            message = msg,
            today = state.today,
            onAddTask = { message ->
                viewModel.convertGmailToTask(message)
                viewingGmailMessage = null
            },
            onAddMeeting = { message ->
                viewModel.convertGmailToMeeting(message)
                viewingGmailMessage = null
            },
            onDismissMessage = { message ->
                viewModel.dismissGmailMessage(message)
                viewingGmailMessage = null
            },
            onDismiss = { viewingGmailMessage = null },
        )
    }

    val currentWhatsApp = viewingWhatsAppMessage?.let { wm -> state.recentWhatsAppMessages.find { it.id == wm.id } ?: wm }
    currentWhatsApp?.let { msg ->
        WhatsAppCardSheet(
            message = msg,
            today = state.today,
            onAddTask = { message ->
                viewModel.convertWhatsAppToTask(message)
                viewingWhatsAppMessage = null
            },
            onAddMeeting = { message ->
                viewModel.convertWhatsAppToMeeting(message)
                viewingWhatsAppMessage = null
            },
            onDismissMessage = { message ->
                viewModel.dismissWhatsAppMessage(message)
                viewingWhatsAppMessage = null
            },
            onDismiss = { viewingWhatsAppMessage = null },
        )
    }

    LazyColumn(
        // The bottom inset clears the floating action button, which would otherwise
        // sit on top of the last row with no way to scroll past it.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "today-top-bar-section") {
            TodayTopBarSection(
                today = state.today,
                userName = userName,
                isOnline = isOnline,
                connectionType = connectionType,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                activePassCount = state.passes.count { !it.isArchived },
                onProfileClick = { showProfileSheet = true },
                onConnectivityClick = { showConnectivityDialog = true },
                onWalletClick = viewModel::openWalletTab,
            )
        }
        item(key = "morning-briefing-hero-card") {
            BriefingHeroCard(
                board = board,
                today = state.today,
                userName = userName,
                isOnline = isOnline,
                onListenClick = { showBriefingSheet = true },
            )
        }
        item(key = "focus-cadence-tile") {
            FocusCadenceCard(board = board)
        }
        item(key = "dashboard-metrics") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Task stats card (Due Today / Overdue / In Progress)
                GlassCard(
                    modifier = Modifier.weight(1f),
                    animatedSheen = false,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Tasks",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "${board.dueToday.size}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Due Today",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (board.overdue.isNotEmpty()) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${board.overdue.size}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        text = "Overdue",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        if (board.inProgress.isNotEmpty()) {
                            Text(
                                text = "${board.inProgress.size} in progress",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }

                // Schedule & Passes card
                GlassCard(
                    modifier = Modifier.weight(1f),
                    animatedSheen = false,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Schedule & Passes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "${board.meetings.size}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Meetings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val passCount = if (board.passes.isNotEmpty()) board.passes.size else state.passes.size
                                Text(
                                    text = "$passCount",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    text = "Passes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (board.completedToday > 0) {
                            Text(
                                text = "${board.completedToday} completed today",
                                style = MaterialTheme.typography.labelSmall,
                                color = DaybookAccents.done.onContainer,
                            )
                        }
                    }
                }
            }
        }

        if (activeSuggestions.isNotEmpty()) {
            item(key = "active-suggestions") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    activeSuggestions.forEach { suggestion ->
                        val key = "${suggestion.type}_${suggestion.relatedEntityType}_${suggestion.relatedEntityId}_${suggestion.body}"
                        AgentSuggestionCard(
                            suggestion = suggestion,
                            onAction = {
                                if (suggestion.relatedEntityType == "task" && suggestion.relatedEntityId != null) {
                                    val targetTask = state.tasks.find { it.id == suggestion.relatedEntityId }
                                    if (targetTask != null) viewingTask = targetTask
                                } else if (suggestion.relatedEntityType == "meeting" && suggestion.relatedEntityId != null) {
                                    val targetMeeting = state.meetings.find { it.id == suggestion.relatedEntityId }
                                    if (targetMeeting != null) viewingMeeting = targetMeeting
                                }
                            },
                            onDismiss = {
                                dismissedSuggestionKeys = dismissedSuggestionKeys + key
                            },
                        )
                    }
                }
            }
        }

        // Active passes widget: horizontal chip row, View More + Add buttons
        val passesToShow = if (board.passes.isNotEmpty()) board.passes else state.passes
        if (passesToShow.isNotEmpty()) {
            item(key = "active-passes") {
                ActivePassesWidget(
                    passes = passesToShow,
                    today = state.today,
                    onPassClick = { pass -> viewingPass = pass },
                    onViewMore = viewModel::openWalletTab,
                    onAdd = viewModel::openWalletAdd,
                )
            }
        }

        val hasSchedule = board.overdue.isNotEmpty() || board.dueToday.isNotEmpty() ||
            board.inProgress.isNotEmpty() || board.upcoming.isNotEmpty() ||
            board.meetings.isNotEmpty() || board.followUps.isNotEmpty() ||
            activeSuggestions.isNotEmpty() || board.completedTasks.isNotEmpty()

        if (!hasSchedule && board.log.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = R.drawable.ic_today,
                    title = stringResource(R.string.today_empty_title),
                    body = stringResource(R.string.today_empty_body),
                )
            }
        } else {
            val filteredOverdue = if (searchQuery.isBlank()) board.overdue
                else board.overdue.filter { it.title.contains(searchQuery, true) || it.notes.contains(searchQuery, true) }
            val filteredDueToday = if (searchQuery.isBlank()) board.dueToday
                else board.dueToday.filter { it.title.contains(searchQuery, true) || it.notes.contains(searchQuery, true) }
            val filteredInProgress = if (searchQuery.isBlank()) board.inProgress
                else board.inProgress.filter { it.title.contains(searchQuery, true) || it.notes.contains(searchQuery, true) }
            val filteredUpcoming = if (searchQuery.isBlank()) board.upcoming
                else board.upcoming.filter { it.title.contains(searchQuery, true) || it.notes.contains(searchQuery, true) }
            val filteredFollowUps = if (searchQuery.isBlank()) board.followUps
                else board.followUps.filter { it.title.contains(searchQuery, true) || it.location.contains(searchQuery, true) }
            val filteredLog = if (searchQuery.isBlank()) board.log
                else board.log.filter { it.body.contains(searchQuery, true) }

            // Upcoming Agenda Timeline Rail
            agendaTimelineSection(
                meetings = board.meetings,
                tasks = board.dueToday,
                today = state.today,
                searchQuery = searchQuery,
                onMeetingClick = { viewingMeeting = it },
                onTaskClick = { viewingTask = it },
            )

            // Late work first, then today's, then anything already started
            boardSection(
                titleRes = R.string.today_section_overdue,
                rows = filteredOverdue,
                keyPrefix = "overdue",
                idOf = { it.id },
            ) { task ->
                TaskRow(
                    task = task,
                    today = state.today,
                    onToggleDone = { viewModel.toggleTaskDone(task) },
                    onClick = { viewingTask = task },
                )
            }
            boardSection(
                titleRes = R.string.today_section_due_today,
                rows = filteredDueToday,
                keyPrefix = "due",
                idOf = { it.id },
            ) { task ->
                TaskRow(
                    task = task,
                    today = state.today,
                    onToggleDone = { viewModel.toggleTaskDone(task) },
                    onClick = { viewingTask = task },
                )
            }
            boardSection(
                titleRes = R.string.today_section_in_progress,
                rows = filteredInProgress,
                keyPrefix = "in-progress",
                idOf = { it.id },
            ) { task ->
                TaskRow(
                    task = task,
                    today = state.today,
                    onToggleDone = { viewModel.toggleTaskDone(task) },
                    onClick = { viewingTask = task },
                )
            }
            boardSection(
                titleRes = R.string.today_section_upcoming,
                rows = filteredUpcoming,
                keyPrefix = "upcoming",
                idOf = { it.id },
            ) { task ->
                TaskRow(
                    task = task,
                    today = state.today,
                    onToggleDone = { viewModel.toggleTaskDone(task) },
                    onClick = { viewingTask = task },
                )
            }
            boardSection(
                titleRes = R.string.today_section_follow_ups,
                rows = filteredFollowUps,
                keyPrefix = "follow-up",
                idOf = { it.id },
            ) { meeting ->
                MeetingRow(
                    meeting = meeting,
                    today = state.today,
                    onClick = { viewingMeeting = meeting },
                    onToggleFollowUp = { viewModel.toggleFollowUpDone(meeting) },
                )
            }
        }
        val filteredLog = if (searchQuery.isBlank()) board.log
            else board.log.filter { it.body.contains(searchQuery, true) }
        logSection(entries = filteredLog, today = state.today, viewModel = viewModel)
        whatsAppSection(
            messages = state.recentWhatsAppMessages,
            onConvert = viewModel::convertWhatsAppAction,
            onDismiss = viewModel::dismissWhatsAppMessage,
            onCardClick = { viewingWhatsAppMessage = it },
        )
        gmailSection(
            messages = visibleGmailMessages,
            onConvert = viewModel::convertGmailAction,
            onDismiss = viewModel::dismissGmailMessage,
            onClearSamples = viewModel::clearSampleGmailMessages,
            onCardClick = { viewingGmailMessage = it },
        )
        if (board.completedTasks.isNotEmpty()) {
            completedTodaySection(
                tasks = board.completedTasks,
                count = board.completedToday,
                isExpanded = completedExpanded,
                onToggleExpand = { completedExpanded = !completedExpanded },
                today = state.today,
                viewModel = viewModel,
                onTaskClick = { task -> viewingTask = task },
            )
        }
    }
}

/**
 * The log section, which unlike the others is drawn even when empty.
 *
 * Writing the log down is the habit the app is trying to encourage, so the add
 * button is always one tap away rather than appearing only once an entry exists.
 */
private fun LazyListScope.logSection(
    entries: List<LogEntry>,
    today: LocalDate,
    viewModel: DaybookViewModel,
) {
    item(key = "log-header") {
        SectionHeader(
            title = stringResource(R.string.today_section_log),
            count = entries.size.takeIf { it > 0 },
        )
    }
    items(items = entries, key = { "log-${it.id}" }) { entry ->
        LogEntryRow(entry = entry, onClick = { viewModel.editLogEntry(entry) })
    }
    item(key = "log-add") {
        TextButton(onClick = { viewModel.newLogEntry(today) }) {
            Text(stringResource(R.string.today_add_log_entry))
        }
    }
}

/**
 * Recent WhatsApp messages, shown below the log when the notification reader is active.
 * Each card shows sender + preview, quick "+ Add Task" / "+ Add Meeting" if actionable,
 * and a "Reply" button that opens WhatsApp to the sender.
 * Hidden entirely when the list is empty (reader off or no messages yet).
 */
private fun LazyListScope.whatsAppSection(
    messages: List<WhatsAppMessage>,
    onConvert: (WhatsAppMessage) -> Unit,
    onDismiss: (WhatsAppMessage) -> Unit,
    onCardClick: ((WhatsAppMessage) -> Unit)? = null,
) {
    if (messages.isEmpty()) return
    item(key = "whatsapp-header") {
        SectionHeader(
            title = "Messages",
            count = messages.size,
        )
    }
    items(items = messages, key = { "wa-${it.id}" }) { msg ->
        WhatsAppMessageCard(msg = msg, onConvert = onConvert, onDismiss = onDismiss, onCardClick = onCardClick)
    }
}

@Composable
private fun WhatsAppMessageCard(
    msg: WhatsAppMessage,
    onConvert: (WhatsAppMessage) -> Unit,
    onDismiss: (WhatsAppMessage) -> Unit,
    onCardClick: ((WhatsAppMessage) -> Unit)? = null,
) {
    val context = LocalContext.current
    val parsed = remember(msg.message) { NaturalLanguageParser.parse(msg.message) }
    val isActionable = parsed.intent != ParsedIntent.UNKNOWN && parsed.intent != ParsedIntent.CONVERSATION

    DaybookCard(
        onClick = if (onCardClick != null) { { onCardClick(msg) } } else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = msg.sender,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (isActionable) {
                    AccentChip(
                        text = if (parsed.intent == ParsedIntent.CREATE_MEETING) "Meeting" else "Task",
                        accent = DaybookAccents.priorityMedium,
                    )
                }
            }
            Text(
                text = msg.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onDismiss(msg) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                }
                if (isActionable) {
                    Button(
                        onClick = { onConvert(msg) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            text = if (parsed.intent == ParsedIntent.CREATE_MEETING) "+ Add Meeting" else "+ Add Task",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                TextButton(
                    onClick = { WhatsAppReplyHelper.openReply(context, msg.sender, "") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Reply", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}


/**
 * A heading plus its rows, or nothing at all when there are no rows.
 *
 * [keyPrefix] is part of every item key because a meeting can appear both in
 * today's meetings and in the follow-ups due below it; without the prefix those
 * two rows would collide on the same key and Compose would reuse the wrong one.
 */
private fun <T> LazyListScope.boardSection(
    @StringRes titleRes: Int,
    rows: List<T>,
    keyPrefix: String,
    idOf: (T) -> Long,
    row: @Composable (T) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "$keyPrefix-header") {
        SectionHeader(
            title = stringResource(titleRes),
            count = rows.size,
        )
    }
    items(items = rows, key = { "$keyPrefix-${idOf(it)}" }) { row(it) }
}

@Composable
private fun TodayTopBarSection(
    today: LocalDate,
    userName: String?,
    isOnline: Boolean,
    connectionType: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activePassCount: Int,
    onProfileClick: () -> Unit,
    onConnectivityClick: () -> Unit,
    onWalletClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Brand Row: App Icon + Name on left, Profile avatar on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = EmeraldTeal.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_today),
                            contentDescription = "Daybook",
                            tint = EmeraldTeal,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = "Daybook",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Surface(
                onClick = onProfileClick,
                shape = CircleShape,
                color = DarkPillBg,
                border = BorderStroke(1.5.dp, EmeraldTeal.copy(alpha = 0.6f)),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initial = userName?.trim()?.firstOrNull()?.uppercase()
                    if (initial != null) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_person),
                            contentDescription = "User Profile",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Date and Inline Search Pill Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${Dates.weekdayLong(today)}, ${Dates.shortLabel(today, today)}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkPillBg,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = "Search",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = SolidColor(EmeraldTeal),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 13.sp,
                                    ),
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = "Clear",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        // Connectivity Status & Wallet Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onConnectivityClick,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isOnline) Color(0xFF34A853) else Color(0xFF9AA0A6),
                                shape = CircleShape,
                            )
                    )
                    Text(
                        text = if (isOnline) "Online ($connectionType)" else "100% On-Device Offline",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                onClick = onWalletClick,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_wallet),
                        contentDescription = "Wallet",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Wallet ($activePassCount)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BriefingHeroCard(
    board: TodayBoard,
    today: LocalDate,
    userName: String?,
    isOnline: Boolean,
    onListenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val quote = remember(board) { BriefingWriter.generate(board) }
    val hour = remember { java.time.LocalTime.now().hour }
    val greetingPrefix = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }
    val greeting = if (!userName.isNullOrBlank()) "$greetingPrefix, $userName!" else "$greetingPrefix!"

    GlassCard(
        tint = HeroNavyBg,
        borderWidth = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onListenClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MORNING BRIEFING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                    color = EmeraldTeal,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E2A38),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        ActiveStatusDot(isActive = true, dotColor = EmeraldTeal, size = 6.dp)
                        Text(
                            text = if (isOnline) "Live Sync" else "100% Offline",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFFE2E8F0),
                        )
                    }
                }
            }

            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                color = Color.White,
            )

            Text(
                text = "\"$quote\"",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = Color(0xFFCBD5E1),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Surface(
                onClick = onListenClick,
                shape = RoundedCornerShape(12.dp),
                color = EmeraldTeal.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, EmeraldTeal.copy(alpha = 0.4f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = "Listen to briefing",
                        tint = EmeraldTeal,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Listen with TTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                        color = EmeraldTeal,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusCadenceCard(
    board: TodayBoard,
    modifier: Modifier = Modifier,
) {
    val activeTaskTitle = board.inProgress.firstOrNull()?.title
        ?: board.dueToday.firstOrNull()?.title
        ?: "Work"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CadenceNavyBg.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = ElectricBlue.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.35f)),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clock),
                        contentDescription = "Focus Clock",
                        tint = ElectricBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Focus Session",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                    color = Color.White,
                )
                Text(
                    text = activeTaskTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            DurationBadge(durationText = "1h 30m")
        }
    }
}

private data class AgendaTimelineEntry(
    val id: String,
    val timeLabel: String,
    val title: String,
    val platformOrBadge: String,
    val durationText: String,
    val isMeeting: Boolean,
    val meeting: Meeting? = null,
    val task: Task? = null,
    val sortMinutes: Int = Int.MAX_VALUE,
)

private fun LazyListScope.agendaTimelineSection(
    meetings: List<Meeting>,
    tasks: List<Task>,
    today: LocalDate,
    searchQuery: String,
    onMeetingClick: (Meeting) -> Unit,
    onTaskClick: (Task) -> Unit,
) {
    val items = mutableListOf<AgendaTimelineEntry>()

    meetings.forEach { m ->
        val time = m.startTime
        val timeStr = time?.let { Dates.timeLabel(it) } ?: "All Day"
        val sortMinutes = if (time != null) time.hour * 60 + time.minute else 9 * 60
        val platform = when {
            m.location.contains("zoom", ignoreCase = true) -> "Zoom"
            m.location.contains("studio", ignoreCase = true) -> "Studio"
            m.location.contains("meet", ignoreCase = true) || m.location.contains("video", ignoreCase = true) -> "Video"
            m.location.isNotBlank() -> m.location
            else -> "Meeting"
        }
        val duration = when {
            m.notes.contains("45m", ignoreCase = true) || m.notes.contains("45 min", ignoreCase = true) -> "45m"
            m.notes.contains("1h", ignoreCase = true) || m.notes.contains("60m", ignoreCase = true) -> "1h"
            m.notes.contains("15m", ignoreCase = true) -> "15m"
            m.notes.contains("30m", ignoreCase = true) || m.notes.contains("30 min", ignoreCase = true) -> "30m"
            else -> "45m"
        }
        items.add(
            AgendaTimelineEntry(
                id = "m-${m.id}",
                timeLabel = timeStr,
                title = m.title,
                platformOrBadge = platform,
                durationText = duration,
                isMeeting = true,
                meeting = m,
                sortMinutes = sortMinutes,
            )
        )
    }

    tasks.forEach { t ->
        val extractedTime = NaturalLanguageParser.extractTime(t.title.lowercase())
        val notesTimeStr = t.notes.lines().find { it.startsWith("Time:", ignoreCase = true) }?.removePrefix("Time:")?.removePrefix("time:")?.trim()
        val timeStr = extractedTime?.let { Dates.timeLabel(it) } ?: notesTimeStr
        if (timeStr != null) {
            val sortMinutes = if (extractedTime != null) {
                extractedTime.hour * 60 + extractedTime.minute
            } else {
                val parsedFromNotes = NaturalLanguageParser.extractTime(timeStr.lowercase())
                if (parsedFromNotes != null) parsedFromNotes.hour * 60 + parsedFromNotes.minute else 10 * 60
            }
            val platform = t.project?.takeIf { it.isNotBlank() } ?: "Task"
            items.add(
                AgendaTimelineEntry(
                    id = "t-${t.id}",
                    timeLabel = timeStr,
                    title = t.title,
                    platformOrBadge = platform,
                    durationText = "30m",
                    isMeeting = false,
                    task = t,
                    sortMinutes = sortMinutes,
                )
            )
        }
    }

    val sortedItems = items.sortedBy { it.sortMinutes }
    val filteredItems = if (searchQuery.isNotBlank()) {
        sortedItems.filter { it.title.contains(searchQuery, ignoreCase = true) || it.platformOrBadge.contains(searchQuery, ignoreCase = true) }
    } else {
        sortedItems
    }

    if (filteredItems.isNotEmpty()) {
        item(key = "agenda-timeline-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Upcoming Agenda",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar),
                            contentDescription = "Calendar view",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "${filteredItems.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, entry ->
            val isFirst = index == 0
            val isLast = index == filteredItems.lastIndex
            TimelineItemRow(
                timeLabel = entry.timeLabel,
                isFirst = isFirst,
                isLast = isLast,
                isActive = (index == 0),
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        if (entry.isMeeting && entry.meeting != null) {
                            onMeetingClick(entry.meeting)
                        } else if (entry.task != null) {
                            onTaskClick(entry.task)
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PlatformBadge(platform = entry.platformOrBadge)
                                DurationBadge(durationText = entry.durationText)
                            }
                        }

                        ActiveStatusDot(isActive = true, dotColor = EmeraldTeal, size = 9.dp)
                    }
                }
            }
        }
    }
}


// â”€â”€ Active Passes Widget â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * Shows up to 4 active (non-expired) passes in a GlassCard on the Today dashboard.
 * Tapping a pass chip opens the dedicated Entry Pass ticket sheet.
 * Action buttons: "View more" → Wallet tab, "+" → add a new pass.
 */
@Composable
fun ActivePassesWidget(
    passes: List<Pass>,
    today: LocalDate,
    onPassClick: (Pass) -> Unit = {},
    onViewMore: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activePasses = remember(passes, today) {
        passes
            .filter { it.expiryDate == null || !it.expiryDate.isBefore(today) }
            .take(4)
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        animatedSheen = false,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_wallet),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.dashboard_passes_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onViewMore) {
                        Text(stringResource(R.string.dashboard_passes_view_more))
                    }
                    FilledTonalIconButton(
                        onClick = onAdd,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.cd_add_pass),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (activePasses.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_passes_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(activePasses, key = { _, p -> p.id }) { index, pass ->
                        var visible by remember(pass.id) { mutableStateOf(false) }
                        LaunchedEffect(pass.id) {
                            delay(index * 60L)
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = slideInVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                initialOffsetY = { it / 2 },
                            ) + fadeIn(),
                        ) {
                            SuggestionChip(
                                onClick = { onPassClick(pass) },
                                label = {
                                    Text(
                                        text = pass.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(categoryIcon(pass.category)),
                                        contentDescription = null,
                                        modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}


// ── Gmail actionable emails section ──────────────────────────────────────────

private fun LazyListScope.gmailSection(
    messages: List<GmailMessage>,
    onConvert: (GmailMessage) -> Unit,
    onDismiss: (GmailMessage) -> Unit,
    onClearSamples: () -> Unit,
    onCardClick: ((GmailMessage) -> Unit)? = null,
) {
    if (messages.isEmpty()) return
    item(key = "gmail-header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "EMAILS & ACTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = " ${messages.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (messages.any { it.messageId.startsWith("offline_") }) {
                TextButton(
                    onClick = onClearSamples,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = "Clear Samples",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    items(items = messages, key = { "gmail-${it.id}" }) { msg ->
        GmailMessageCard(msg = msg, onConvert = onConvert, onDismiss = onDismiss, onCardClick = onCardClick)
    }
}

@Composable
private fun GmailMessageCard(
    msg: GmailMessage,
    onConvert: (GmailMessage) -> Unit,
    onDismiss: (GmailMessage) -> Unit,
    onCardClick: ((GmailMessage) -> Unit)? = null,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val isSample = msg.messageId.startsWith("offline_")

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick?.invoke(msg) ?: run { expanded = !expanded } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = msg.sender,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isSample) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            Text(
                                text = "Sample",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                if (msg.suggestedAction != null) {
                    AccentChip(
                        text = "Action detected",
                        accent = DaybookAccents.priorityMedium,
                    )
                }
            }
            Text(
                text = msg.subject,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (msg.snippet.isNotBlank()) {
                Text(
                    text = if (expanded) msg.snippet else (msg.snippet.take(90) + if (msg.snippet.length > 90) "…" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://mail.google.com")
                            setPackage("com.google.android.gm")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com"))
                            context.startActivity(webIntent)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Open Gmail",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                if (msg.suggestedAction != null && msg.actionedAt == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onDismiss(msg) }) {
                            Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { onConvert(msg) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (msg.suggestedAction.startsWith("Meeting:", ignoreCase = true)) "+ Add Meeting" else "+ Add Task",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (msg.actionedAt != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE6F4EA),
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text(
                                    text = "Added",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF137333),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        TextButton(onClick = { onDismiss(msg) }) {
                            Text(if (msg.actionedAt != null) "Remove" else "Dismiss", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentSuggestionCard(
    suggestion: AgentSuggestion,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        animatedSheen = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val label = when (suggestion.type) {
                        SuggestionType.STALE_TASK -> "Stale Task Reminder"
                        SuggestionType.FOLLOW_UP_DUE -> "Meeting Follow-up"
                        SuggestionType.CADENCE_DUE -> "Recurring Task Due"
                    }
                    val iconRes = when (suggestion.type) {
                        SuggestionType.STALE_TASK -> R.drawable.ic_clock
                        SuggestionType.FOLLOW_UP_DUE -> R.drawable.ic_meetings
                        SuggestionType.CADENCE_DUE -> R.drawable.ic_tasks
                    }
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = suggestion.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (suggestion.relatedEntityId != null) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("View", style = MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Dismiss suggestion",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Collapsible section for tasks completed today, allowing users to view finished items
 * and uncheck tasks if completed by mistake.
 */
private fun LazyListScope.completedTodaySection(
    tasks: List<Task>,
    count: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    today: LocalDate,
    viewModel: DaybookViewModel,
    onTaskClick: ((Task) -> Unit)? = null,
) {
    if (tasks.isEmpty()) return
    item(key = "completed-today-header") {
        val chevronRotation by animateFloatAsState(
            targetValue = if (isExpanded) 180f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "completed_chevron_rotation",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(top = 16.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = if (isExpanded) "Collapse completed tasks" else "Expand completed tasks",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation),
                )
                Text(
                    text = "COMPLETED TODAY ($count)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (isExpanded) "Hide" else "Show",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (isExpanded) {
        items(items = tasks, key = { "completed-${it.id}" }) { task ->
            TaskRow(
                task = task,
                today = today,
                onToggleDone = { viewModel.toggleTaskDone(task) },
                onClick = { onTaskClick?.invoke(task) ?: viewModel.toggleTaskDone(task) },
            )
        }
    }
}