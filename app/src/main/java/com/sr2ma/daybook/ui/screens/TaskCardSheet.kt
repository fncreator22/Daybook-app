package com.sr2ma.daybook.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.ChecklistParser
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.NaturalLanguageParser
import com.sr2ma.daybook.domain.model.Cadence
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import com.sr2ma.daybook.ui.components.OverflowActionItem
import com.sr2ma.daybook.ui.components.OverflowActionMenu
import com.sr2ma.daybook.ui.theme.CoralRed
import com.sr2ma.daybook.ui.theme.DarkPillBg
import com.sr2ma.daybook.ui.theme.DaybookAccents
import com.sr2ma.daybook.ui.theme.ElectricBlue
import com.sr2ma.daybook.ui.theme.EmeraldTeal
import java.time.LocalDate

/**
 * Dedicated read-only "Task Details" presentation sheet.
 *
 * Implements:
 * - Header with dismiss ("X"), title ("Task Details"), and 3-dot overflow menu trigger
 * - Floating popup overflow menu (Edit Task, Mark Complete, Share, Move, Delete)
 * - Title with inline priority flame badge (#FF5A5F) and category pill
 * - Metadata rows: Calendar date, Clock time range (if present), Location pin (if present), Recurrence (if set)
 * - Horizontal colored topic tags (#Marketing, #Strategy, #Goals, etc.) when present
 * - Markdown subtask checklist section with live Progress: X/Y indicator and interactive toggles
 * - Bottom comment dock ("Add Comment/Note...")
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskCardSheet(
    task: Task,
    today: LocalDate,
    onEdit: (Task) -> Unit,
    onToggleDone: (Task) -> Unit,
    onDelete: ((Task) -> Unit)? = null,
    onUpdateTask: ((Task) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isOverdue = task.isOverdue(today)
    val isDone = task.status == TaskStatus.DONE
    var showOverflowMenu by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    var localNotes by remember(task.id, task.notes) { mutableStateOf(task.notes) }

    val parsedLocation = remember(localNotes) {
        localNotes.lines().find { it.startsWith("Location:", ignoreCase = true) }
            ?.removePrefix("Location:")?.removePrefix("location:")?.trim()
    }
    val parsedTime = remember(localNotes, task.title) {
        val line = localNotes.lines().find { it.startsWith("Time:", ignoreCase = true) }
            ?.removePrefix("Time:")?.removePrefix("time:")?.trim()
        line ?: NaturalLanguageParser.extractTime(task.title.lowercase())?.toString()
    }

    // Subtask checklist parsing
    val checklistItems = remember(localNotes) { ChecklistParser.parse(localNotes) }
    val checklistSummary = remember(checklistItems) { ChecklistParser.getSummary(checklistItems) }
    val descriptionProse = remember(localNotes) { ChecklistParser.extractDescription(localNotes) }
    val attachments = remember(localNotes) { ChecklistParser.extractAttachments(localNotes) }

    // Topics extraction
    val topics = remember(localNotes, task.project) {
        ChecklistParser.extractTopics(localNotes, task.project)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Text(
                    text = "Task Details",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    OverflowActionMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        actions = buildList {
                            add(
                                OverflowActionItem(
                                    label = "Edit Task",
                                    iconRes = R.drawable.ic_edit,
                                    onClick = {
                                        onDismiss()
                                        onEdit(task)
                                    }
                                )
                            )
                            add(
                                OverflowActionItem(
                                    label = if (isDone) "Reopen Task" else "Mark Complete",
                                    iconRes = R.drawable.ic_check,
                                    onClick = {
                                        onToggleDone(task)
                                        onDismiss()
                                    }
                                )
                            )
                            add(
                                OverflowActionItem(
                                    label = "Share",
                                    iconRes = R.drawable.ic_share,
                                    onClick = {
                                        val shareText = buildString {
                                            appendLine("Task: ${task.title}")
                                            if (task.dueDate != null) appendLine("Due: ${Dates.shortLabel(task.dueDate, today)}")
                                            if (localNotes.isNotBlank()) appendLine("\n$localNotes")
                                        }
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, task.title)
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Task"))
                                    }
                                )
                            )
                            add(
                                OverflowActionItem(
                                    label = "Move",
                                    iconRes = R.drawable.ic_calendar,
                                    onClick = {
                                        onDismiss()
                                        onEdit(task)
                                    }
                                )
                            )
                            if (onDelete != null) {
                                add(
                                    OverflowActionItem(
                                        label = "Delete",
                                        iconRes = R.drawable.ic_delete,
                                        isDestructive = true,
                                        onClick = {
                                            onDismiss()
                                            onDelete(task)
                                        }
                                    )
                                )
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Sub-label Title
            Text(
                text = "TITLE",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            // Task Title Row with Flame Badge and Category Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )

                // Inline Priority Flame Badge
                if (task.priority == Priority.HIGH || task.priority == Priority.URGENT) {
                    Surface(
                        shape = CircleShape,
                        color = CoralRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, CoralRed.copy(alpha = 0.5f)),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_flame),
                                contentDescription = "High Priority",
                                tint = CoralRed,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                // Main Category Pill (Project or primary topic tag)
                val mainCategory = task.project?.takeIf { it.isNotBlank() }
                    ?: topics.firstOrNull()?.removePrefix("#")
                if (mainCategory != null) {
                    val categoryAccent = DaybookAccents.topicAccent(mainCategory)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = categoryAccent.container,
                        border = BorderStroke(1.dp, categoryAccent.onContainer.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = if (mainCategory.startsWith("#")) mainCategory else "#$mainCategory",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            ),
                            color = categoryAccent.onContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Metadata Rows: Calendar Date, Clock Time Range, Location Pin, Recurrence
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(14.dp),
            ) {
                // Calendar Date Row
                val dateLabel = if (task.dueDate != null) {
                    Dates.weekdayLong(task.dueDate) + ", " + Dates.shortLabel(task.dueDate, today)
                } else {
                    Dates.weekdayLong(today) + ", " + Dates.shortLabel(today, today)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar),
                        contentDescription = "Date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (isOverdue) CoralRed else MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Clock Time Range Row (only if time specified)
                if (parsedTime != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clock),
                            contentDescription = "Time",
                            tint = ElectricBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = parsedTime,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Location Pin Row (only if location specified)
                if (!parsedLocation.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_location),
                            contentDescription = "Location",
                            tint = EmeraldTeal,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = parsedLocation,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Recurrence Row (if set)
                if (task.cadence != Cadence.NONE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sync),
                            contentDescription = "Recurrence",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = task.cadence.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Topics Section (only if topics exist)
            if (topics.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "TOPICS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    topics.forEach { topic ->
                        val accent = DaybookAccents.topicAccent(topic)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accent.container,
                            border = BorderStroke(1.dp, accent.onContainer.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = topic,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                ),
                                color = accent.onContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            // Description Block (if present)
            if (descriptionProse.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "DESCRIPTION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = descriptionProse,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                )
            }

            // Attachments Preview (only if documents/attachments exist)
            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "ATTACHMENTS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    attachments.forEach { fileName ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_pass_other),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Subtasks Section Header with Live Progress Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SUBTASKS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (checklistSummary.total > 0) {
                    Text(
                        text = "Progress: ${checklistSummary.progressText}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                            color = EmeraldTeal,
                        ),
                    )
                }
            }

            if (checklistSummary.total > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { checklistSummary.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = EmeraldTeal,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Subtask Checklist Items
            if (checklistItems.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    checklistItems.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val newNotes = ChecklistParser.toggleItem(localNotes, item.index)
                                    localNotes = newNotes
                                    val updatedTask = task.copy(notes = newNotes)
                                    onUpdateTask?.invoke(updatedTask)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                        ) {
                            // Custom Rounded Checkbox
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(
                                        color = if (item.isChecked) ElectricBlue else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (item.isChecked) ElectricBlue else Color(0xFF4A5568),
                                        shape = RoundedCornerShape(6.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (item.isChecked) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = "Completed",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }

                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = if (item.isChecked) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No subtasks yet. Add subtasks using '- [ ] description' in notes or append comments below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            // Bottom Quick Comment / Note Dock
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DarkPillBg,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                ) {
                    TextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = {
                            Text(
                                text = "Add Comment/Note...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (commentInput.isNotBlank()) {
                                    val newNotes = ChecklistParser.appendComment(localNotes, commentInput)
                                    localNotes = newNotes
                                    val updatedTask = task.copy(notes = newNotes)
                                    onUpdateTask?.invoke(updatedTask)
                                    commentInput = ""
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = {
                            if (commentInput.isNotBlank()) {
                                val newNotes = ChecklistParser.appendComment(localNotes, commentInput)
                                localNotes = newNotes
                                val updatedTask = task.copy(notes = newNotes)
                                onUpdateTask?.invoke(updatedTask)
                                commentInput = ""
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_send),
                            contentDescription = "Send note",
                            tint = EmeraldTeal,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Primary Bottom Actions
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
                    Text(if (isDone) "Reopen Task" else "Mark Complete")
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
