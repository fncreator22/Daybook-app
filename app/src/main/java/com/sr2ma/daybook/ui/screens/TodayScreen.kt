package com.sr2ma.daybook.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.Pass
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
import java.time.LocalDate
import kotlinx.coroutines.delay

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
    modifier: Modifier = Modifier,
) {
    val board = state.board

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
        item(key = "quick-add") {
            QuickAddField(onAdd = viewModel::quickAddTask)
        }

        // Active passes widget: horizontal chip row, View More + Add buttons
        if (state.passes.isNotEmpty()) {
            item(key = "active-passes") {
                ActivePassesWidget(
                    passes = state.passes,
                    today = state.today,
                    onViewMore = viewModel::openWalletTab,
                    onAdd = viewModel::openWalletAdd,
                )
            }
        }

        val hasSchedule = board.overdue.isNotEmpty() || board.dueToday.isNotEmpty() ||
            board.inProgress.isNotEmpty() || board.upcoming.isNotEmpty() ||
            board.meetings.isNotEmpty() || board.followUps.isNotEmpty()

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
                    onClick = { viewModel.editTask(task) },
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
                    onClick = { viewModel.editTask(task) },
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
                    onClick = { viewModel.editTask(task) },
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
                    onClick = { viewModel.editTask(task) },
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
                    onClick = { viewModel.editMeeting(meeting) },
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
                    onClick = { viewModel.editMeeting(meeting) },
                    onToggleFollowUp = { viewModel.toggleFollowUpDone(meeting) },
                )
            }
        }
        logSection(entries = board.log, today = state.today, viewModel = viewModel)
        whatsAppSection(messages = state.recentWhatsAppMessages)
        gmailSection(messages = state.recentGmailMessages, onConvert = viewModel::convertGmailAction, onDismiss = viewModel::dismissGmailMessage)
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
            // The list's contentPadding does not reach a header's own text, so the
            // inset is passed here rather than baked into SectionHeader.
            modifier = Modifier.padding(horizontal = 16.dp),
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
 * Each card shows sender + preview and a "Reply" button that opens WhatsApp to the sender.
 * Hidden entirely when the list is empty (reader off or no messages yet).
 */
private fun LazyListScope.whatsAppSection(messages: List<WhatsAppMessage>) {
    if (messages.isEmpty()) return
    item(key = "whatsapp-header") {
        SectionHeader(
            title = "Messages",
            count = messages.size,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    items(items = messages, key = { "wa-${it.id}" }) { msg ->
        WhatsAppMessageCard(msg)
    }
}

@Composable
private fun WhatsAppMessageCard(msg: WhatsAppMessage) {
    val context = LocalContext.current
    DaybookCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = msg.sender,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = msg.message.take(80) + if (msg.message.length > 80) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            TextButton(
                onClick = { WhatsAppReplyHelper.openReply(context, msg.sender, "") },
            ) {
                Text("Reply")
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
            modifier = Modifier.padding(horizontal = 16.dp),
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

/**
 * One line in, one task out, due today.
 *
 * Everything else about the task takes its default, because the point of this
 * field is to get a thought out of your head in one gesture; the full editor is
 * still one tap away on the row it creates.
 */
@Composable
private fun QuickAddField(
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Saveable, not plain `remember`: this field lives inside a LazyColumn item,
    // and a half-typed line would otherwise be thrown away as soon as the item
    // scrolled out of the viewport.
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text.trim())
            text = ""
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(R.string.today_quick_add_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        // The button only appears once there is something to add, so an empty field
        // is a plain prompt rather than a control with a dead affordance on it.
        trailingIcon = {
            if (text.isNotBlank()) {
                IconButton(onClick = submit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.cd_add_task),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

// â”€â”€ Active Passes Widget â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * Shows up to 3 active (non-expired) passes as horizontal chips on the Today dashboard.
 * Two action buttons: "View more" â†’ Wallet tab, "+" â†’ add a new pass.
 */
@Composable
fun ActivePassesWidget(
    passes: List<Pass>,
    today: LocalDate,
    onViewMore: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activePasses = remember(passes, today) {
        passes
            .filter { it.expiryDate == null || !it.expiryDate.isBefore(today) }
            .take(3)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.dashboard_passes_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                modifier = Modifier.padding(bottom = 4.dp),
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
                            onClick = onViewMore,
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
        Spacer(Modifier.height(4.dp))
    }
}


// ── Gmail actionable emails section ──────────────────────────────────────────

private fun LazyListScope.gmailSection(
    messages: List<GmailMessage>,
    onConvert: (GmailMessage) -> Unit,
    onDismiss: (GmailMessage) -> Unit,
) {
    if (messages.isEmpty()) return
    item(key = "gmail-header") {
        SectionHeader(
            title = "Emails & Actions",
            count = messages.size,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
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
    GlassCard(modifier = Modifier.fillMaxWidth()) {
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
                Text(
                    text = msg.sender,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
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
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (msg.snippet.isNotBlank()) {
                Text(
                    text = msg.snippet.take(90) + if (msg.snippet.length > 90) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (msg.suggestedAction != null && msg.actionedAt == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onDismiss(msg) }) {
                        Text("Dismiss")
                    }
                    Button(
                        onClick = { onConvert(msg) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (msg.suggestedAction.startsWith("Meeting:", ignoreCase = true)) "+ Add Meeting" else "+ Add Task",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}