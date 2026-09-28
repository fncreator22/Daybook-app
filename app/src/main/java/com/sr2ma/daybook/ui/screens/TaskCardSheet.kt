package com.sr2ma.daybook.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.model.Cadence
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import java.time.LocalDate

/**
 * Dedicated read-only "Task Card" presentation sheet.
 *
 * Shown when tapping a task row on the Today dashboard or Tasks screen so the user
 * is presented with a clean presentation card with overflow actions rather than
 * immediately entering edit form fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCardSheet(
    task: Task,
    today: LocalDate,
    onEdit: (Task) -> Unit,
    onToggleDone: (Task) -> Unit,
    onDelete: ((Task) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isOverdue = task.isOverdue(today)
    val isDone = task.status == TaskStatus.DONE
    var showMenu by remember { mutableStateOf(false) }

    val parsedLocation = remember(task.notes) {
        task.notes.lines().find { it.startsWith("Location:", ignoreCase = true) }
            ?.removePrefix("Location:")?.removePrefix("location:")?.trim()
    }
    val parsedTime = remember(task.notes, task.title) {
        val line = task.notes.lines().find { it.startsWith("Time:", ignoreCase = true) }
            ?.removePrefix("Time:")?.removePrefix("time:")?.trim()
        line ?: com.sr2ma.daybook.domain.NaturalLanguageParser.extractTime(task.title.lowercase())?.toString()
    }
    val parsedSource = remember(task.notes) {
        task.notes.lines().find { it.startsWith("Source:", ignoreCase = true) }
            ?.removePrefix("Source:")?.removePrefix("source:")
            ?.replace(Regex("\\[msgId:[^\\]]+\\]"), "")?.trim()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isDone -> Color(0xFFE6F4EA)
                        isOverdue -> MaterialTheme.colorScheme.errorContainer
                        task.status == TaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                ) {
                    Text(
                        text = when {
                            isDone -> "COMPLETED"
                            isOverdue -> "OVERDUE"
                            task.status == TaskStatus.IN_PROGRESS -> "IN PROGRESS"
                            task.isDueOn(today) -> "DUE TODAY"
                            else -> "TASK"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isDone -> Color(0xFF137333)
                            isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                            task.status == TaskStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = "More options",
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Task") },
                                onClick = {
                                    showMenu = false
                                    onDismiss()
                                    onEdit(task)
                                }
                            )
                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete Task", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDismiss()
                                        onDelete(task)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Task Presentation Card ──────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(20.dp),
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(16.dp))

                    // Priority
                    if (task.priority != Priority.MEDIUM) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = when (task.priority) {
                                    Priority.URGENT -> MaterialTheme.colorScheme.errorContainer
                                    Priority.HIGH -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_tasks),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Priority",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = task.priority.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // Due Date
                    if (task.dueDate != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isOverdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_clock),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = if (parsedTime != null) "Due Date & Time" else "Due Date",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val dueLabel = Dates.shortLabel(task.dueDate, today)
                                val fullTimeLabel = if (parsedTime != null) "$dueLabel at $parsedTime" else dueLabel
                                Text(
                                    text = fullTimeLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // Location
                    if (!parsedLocation.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_meetings),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Location / Platform",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = parsedLocation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // Source
                    if (!parsedSource.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_today),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Source",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = parsedSource,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // Project / Topics
                    if (!task.project.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_star),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Project / Topic",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = task.project,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // Recurrence
                    if (task.cadence != Cadence.NONE) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_sync),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Recurrence",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = task.cadence.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // Notes / Description / Parsed Details
                    if (task.notes.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "DETAILS & NOTES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = task.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onToggleDone(task)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isDone) "Reopen Task" else "Mark Done")
                }

                Button(
                    onClick = {
                        onDismiss()
                        onEdit(task)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Edit Task")
                }
            }
        }
    }
}
