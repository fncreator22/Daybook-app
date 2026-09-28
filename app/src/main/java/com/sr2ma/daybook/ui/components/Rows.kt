package com.sr2ma.daybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import com.sr2ma.daybook.ui.accent
import com.sr2ma.daybook.ui.dayLabel
import com.sr2ma.daybook.ui.labelRes
import com.sr2ma.daybook.ui.latenessLabel
import com.sr2ma.daybook.ui.theme.CoralRed
import com.sr2ma.daybook.ui.theme.DaybookAccents
import java.time.LocalDate

/**
 * The list rows.
 *
 * Today, Tasks, Log and Meetings all show the same three kinds of thing, so they
 * share these rather than each drawing its own. Anything a screen wants to vary
 * is a flag with a default, which keeps the call sites short.
 */

/**
 * A tight row of chips under a title, wrapping onto a second line when needed.
 *
 * A [FlowRow] rather than a [Row]: with a long project or location, or at a large
 * system font size, a plain Row had to squeeze the chips towards zero width and
 * the labels ellipsized down to nothing. Wrapping costs one line of height and
 * keeps every chip readable.
 *
 * The content block stays scoped to [RowScope] — `FlowRowScope` is a `RowScope`,
 * so nothing at a call site has to change.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(content: @Composable RowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) { content() }
}

/**
 * One task. The checkbox completes it; the rest of the row opens the editor.
 *
 * The priority chip only appears when the priority is not the default, so a list
 * of ordinary work stays quiet and anything deliberately marked up stands out.
 */
@Composable
fun TaskRow(
    task: Task,
    today: LocalDate,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProject: Boolean = true,
) {
    val isDone = task.status == TaskStatus.DONE
    val lateness = latenessLabel(task.dueDate.takeIf { task.isOpen }, today)
    // Resolved out here because Modifier.semantics is not a composable scope.
    val markComplete = stringResource(R.string.tasks_mark_complete)
    val markIncomplete = stringResource(R.string.tasks_mark_incomplete)

    DaybookCard(onClick = onClick, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        ) {
            Checkbox(
                checked = isDone,
                onCheckedChange = { onToggleDone() },
                modifier = Modifier.semantics {
                    contentDescription = if (isDone) markIncomplete else markComplete
                },
            )
            Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (task.status.isClosed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (task.priority == Priority.HIGH || task.priority == Priority.URGENT) {
                        Icon(
                            painter = painterResource(R.drawable.ic_flame),
                            contentDescription = "High Priority",
                            tint = CoralRed,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                TaskMeta(task, today, lateness, showProject)
            }
        }
    }
}

/**
 * The chips under a task title: when it is due, whether it is late, how it is
 * marked up, and which project it belongs to.
 *
 * [lateness] is passed in rather than computed here because the caller has
 * already resolved it, and resolving a string twice per row is wasted work.
 */
@Composable
private fun TaskMeta(
    task: Task,
    today: LocalDate,
    lateness: String?,
    showProject: Boolean,
) {
    val completedOn = task.completedAt
        ?.takeIf { task.status == TaskStatus.DONE }
        ?.let { Dates.localDateOf(it) }
    val project = task.project?.takeIf { showProject && it.isNotBlank() }
    // A due date is only news while the task is still open; once it is closed the
    // completion date is the more useful thing to show.
    val due = task.dueDate?.takeIf { task.isOpen }
    // Open and Done are already obvious from the checkbox, so only the two states
    // that the checkbox cannot express get a chip.
    val showStatus = task.status == TaskStatus.IN_PROGRESS || task.status == TaskStatus.CANCELLED

    // Nothing to say about a plain, undated, unfiled task, so draw no row at all
    // rather than an empty one that still costs vertical space.
    if (due == null && completedOn == null && project == null &&
        task.priority == Priority.MEDIUM && !showStatus
    ) {
        return
    }

    MetaRow {
        if (due != null) {
            AccentChip(
                text = lateness ?: dayLabel(due, today),
                accent = if (lateness != null) DaybookAccents.overdue else DaybookAccents.neutral,
            )
        }
        if (completedOn != null) {
            AccentChip(
                text = stringResource(R.string.tasks_completed_on, dayLabel(completedOn, today)),
                accent = DaybookAccents.done,
            )
        }
        if (showStatus) {
            AccentChip(
                text = stringResource(task.status.labelRes),
                accent = DaybookAccents.neutral,
            )
        }
        // Medium is the default, so showing it would put a chip on almost every
        // row and stop the marked-up ones from standing out.
        if (task.priority != Priority.MEDIUM) {
            AccentChip(
                text = stringResource(task.priority.labelRes),
                accent = task.priority.accent,
            )
        }
        if (project != null) {
            QuietChip(text = project)
        }
    }
}

/**
 * One meeting. The row opens the editor; the trailing button toggles the follow-up
 * without opening anything, because that is the action taken most often.
 *
 * [showDay] is off on the Today screen, where every meeting is by definition today
 * and the chip would say the same thing on every row.
 */
@Composable
fun MeetingRow(
    meeting: Meeting,
    today: LocalDate,
    onClick: () -> Unit,
    onToggleFollowUp: () -> Unit,
    modifier: Modifier = Modifier,
    showDay: Boolean = true,
) {
    val attendees = meeting.attendeeList.size
    val startTime = meeting.startTime
    val followUpOverdue = meeting.needsFollowUpBy(today)

    DaybookCard(onClick = onClick, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaRow {
                    if (startTime != null) {
                        AccentChip(
                            text = Dates.timeLabel(startTime),
                            accent = DaybookAccents.neutral,
                        )
                    }
                    if (showDay) {
                        QuietChip(text = dayLabel(meeting.day, today))
                    }
                    if (attendees > 0) {
                        QuietChip(text = stringResource(R.string.meetings_attendee_count, attendees))
                    }
                    if (meeting.location.isNotBlank()) {
                        QuietChip(text = meeting.location)
                    }
                }
                FollowUpChip(meeting, today, followUpOverdue)
            }
            // Shown for any meeting that has a follow-up, done or not, so that a
            // mis-tapped tick can be undone from the list instead of only from
            // inside the editor.
            if (meeting.nextTouch != null) {
                IconButton(onClick = onToggleFollowUp) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(
                            if (meeting.followUpDone) {
                                R.string.meetings_reopen_follow_up
                            } else {
                                R.string.meetings_mark_follow_up_done
                            },
                        ),
                        tint = if (meeting.followUpDone) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

/**
 * The follow-up state, on its own line below the other chips.
 *
 * It sits apart because it is the one piece of meeting metadata that is a claim on
 * the future rather than a description of the past, and burying it among the time
 * and location chips made it easy to miss.
 */
@Composable
private fun FollowUpChip(meeting: Meeting, today: LocalDate, overdue: Boolean) {
    val nextTouch = meeting.nextTouch ?: return

    MetaRow {
        if (meeting.followUpDone) {
            AccentChip(
                text = stringResource(R.string.meetings_follow_up_done),
                accent = DaybookAccents.done,
            )
        } else {
            AccentChip(
                text = stringResource(
                    R.string.meetings_follow_up_due,
                    dayLabel(nextTouch, today),
                ),
                accent = if (overdue) DaybookAccents.overdue else DaybookAccents.neutral,
            )
        }
    }
}

/**
 * One log entry. The kind chip leads, because scanning a day for "what blocked me"
 * is the common way to read the log back.
 */
@Composable
fun LogEntryRow(
    entry: LogEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaybookCard(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            AccentChip(
                text = stringResource(entry.kind.labelRes),
                accent = entry.kind.accent,
            )
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
