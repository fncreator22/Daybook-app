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
    var showConnectivityDialog by remember { mutableStateOf(false) }
    var showBriefingSheet by remember { mutableStateOf(false) }
    var dismissedSuggestionKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }

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
            onDismiss = { viewingTask = null },
        )
    }

    LazyColumn(
        // The bottom inset clears the floating action button, which would otherwise
        // sit on top of the last row with no way to scroll past it.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "day-header") {
            DayHeader(
                today = state.today,
                completedToday = board.completedToday,
                userName = userName,
            )
        }
        item(key = "status-indicator") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { showConnectivityDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
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

                // Prominent Glass Pill button for Wallet
                Surface(
                    onClick = { viewModel.openWalletTab() },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    ),
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
                        val activePassCount = state.passes.count { !it.isArchived }
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
        item(key = "morning-briefing-card") {
            val briefingSummary = remember(board) { BriefingWriter.generate(board) }
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBriefingSheet = true }
                    .padding(vertical = 2.dp),
                animatedSheen = false,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_today),
                                contentDescription = "Morning Briefing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Morning Briefing",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "• Tap to listen",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = briefingSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    IconButton(
                        onClick = { showBriefingSheet = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic),
                            contentDescription = "Listen to briefing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
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
            // Late work first, then today's, then anything already started: the order
            // a person would work down the list in.
            boardSection(
                titleRes = R.string.today_section_overdue,
                rows = board.overdue,
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
                rows = board.dueToday,
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
                rows = board.inProgress,
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
                rows = board.upcoming,
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
                titleRes = R.string.today_section_meetings,
                rows = board.meetings,
                keyPrefix = "meeting",
                idOf = { it.id },
            ) { meeting ->
                MeetingRow(
                    meeting = meeting,
                    today = state.today,
                    onClick = { viewingMeeting = meeting },
                    onToggleFollowUp = { viewModel.toggleFollowUpDone(meeting) },
                    // Every meeting in this section is today's by definition, so a
                    // "Today" chip on each row would say nothing.
                    showDay = false,
                )
            }
            boardSection(
                titleRes = R.string.today_section_follow_ups,
                rows = board.followUps,
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
        logSection(entries = board.log, today = state.today, viewModel = viewModel)
        whatsAppSection(
            messages = state.recentWhatsAppMessages,
            onConvert = viewModel::convertWhatsAppAction,
        )
        gmailSection(
            messages = state.recentGmailMessages,
            onConvert = viewModel::convertGmailAction,
            onDismiss = viewModel::dismissGmailMessage,
            onClearSamples = viewModel::clearSampleGmailMessages,
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
) {
    if (messages.isEmpty()) return
    item(key = "whatsapp-header") {
        SectionHeader(
            title = "Messages",
            count = messages.size,
        )
    }
    items(items = messages, key = { "wa-${it.id}" }) { msg ->
        WhatsAppMessageCard(msg = msg, onConvert = onConvert)
    }
}

@Composable
private fun WhatsAppMessageCard(
    msg: WhatsAppMessage,
    onConvert: (WhatsAppMessage) -> Unit,
) {
    val context = LocalContext.current
    val parsed = remember(msg.message) { NaturalLanguageParser.parse(msg.message) }
    val isActionable = parsed.intent != ParsedIntent.UNKNOWN && parsed.intent != ParsedIntent.CONVERSATION

    DaybookCard {
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

/** The date, written out, with a quiet tally of what has already been finished. */
@Composable
private fun DayHeader(
    today: LocalDate,
    completedToday: Int,
    userName: String? = null,
) {
    GlassCard(
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        animatedSheen = true,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val greeting = if (userName != null) {
                val hour = java.time.LocalTime.now().hour
                val prefix = when (hour) {
                    in 5..11 -> "Good morning"
                    in 12..16 -> "Good afternoon"
                    else -> "Good evening"
                }
                "$prefix, $userName"
            } else {
                Dates.weekdayLong(today)
            }
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = if (userName != null) "${Dates.weekdayLong(today)} \u2014 ${Dates.shortLabel(today, today)}" else Dates.shortLabel(today, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (completedToday > 0) {
                    AccentChip(
                        text = stringResource(R.string.today_completed_count, completedToday),
                        accent = DaybookAccents.done,
                    )
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
        GmailMessageCard(msg = msg, onConvert = onConvert, onDismiss = onDismiss)
    }
}

@Composable
private fun GmailMessageCard(
    msg: GmailMessage,
    onConvert: (GmailMessage) -> Unit,
    onDismiss: (GmailMessage) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val isSample = msg.messageId.startsWith("offline_")

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
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
    @Suppress("UNUSED_PARAMETER") onTaskClick: ((Task) -> Unit)? = null,
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
                onClick = { viewModel.toggleTaskDone(task) },
            )
        }
    }
}