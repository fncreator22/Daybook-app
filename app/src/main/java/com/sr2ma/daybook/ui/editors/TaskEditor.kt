package com.sr2ma.daybook.ui.editors

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import com.sr2ma.daybook.ui.accent
import com.sr2ma.daybook.ui.components.ConfirmDialog
import com.sr2ma.daybook.ui.components.DateField
import com.sr2ma.daybook.ui.components.LabeledTextField
import com.sr2ma.daybook.ui.components.QuietChip
import com.sr2ma.daybook.ui.components.SegmentedSelector
import com.sr2ma.daybook.ui.labelRes
import java.time.LocalDate

/**
 * The whole of a task on one screen.
 *
 * The screen edits a copy and hands the finished [Task] back on save, so
 * abandoning the editor cannot leave a half-typed task in the database. Trimming
 * and the completion timestamp are the repository's job, which is why nothing
 * here touches them.
 *
 * [meetingTitle] is the meeting this task came out of, if any. It is shown but
 * not editable: moving an action item to a different meeting is not a thing
 * anyone asks for, and the link is only there to give the task context.
 */
@Composable
fun TaskEditor(
    seed: Task,
    today: LocalDate,
    projects: List<String>,
    meetingTitle: String?,
    onSave: (Task) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the id, not the whole seed: opening a different task has to start a
    // fresh draft, but the repository also re-emits its list after every write, and
    // a value key would treat that new-but-equal seed as a different task and throw
    // away the edits in progress.
    var draft by remember(seed.id) { mutableStateOf(seed) }
    var titleTouched by remember(seed.id) { mutableStateOf(false) }
    var askDelete by remember(seed.id) { mutableStateOf(false) }

    val isNew = seed.id == 0L
    // The error only appears once the field has been touched: a new task starts
    // empty, and greeting someone with a validation error is rude.
    val titleError = if (titleTouched && draft.title.isBlank()) {
        stringResource(R.string.error_title_required)
    } else {
        null
    }

    EditorScaffold(
        title = stringResource(if (isNew) R.string.tasks_new else R.string.tasks_edit),
        saveEnabled = draft.title.isNotBlank(),
        onSave = { onSave(draft) },
        onDismiss = onDismiss,
        // Parenthesised because an unwrapped `else { … }` would be read as a block
        // rather than as the lambda this parameter wants.
        onDelete = if (isNew) null else ({ askDelete = true }),
        modifier = modifier,
    ) {
        LabeledTextField(
            value = draft.title,
            onValueChange = { text ->
                titleTouched = true
                draft = draft.copy(title = text)
            },
            label = stringResource(R.string.tasks_field_title),
            error = titleError,
        )

        SegmentedSelector(
            label = stringResource(R.string.tasks_field_priority),
            options = Priority.entries,
            selected = draft.priority,
            optionLabel = { priority -> stringResource(priority.labelRes) },
            onSelect = { priority -> draft = draft.copy(priority = priority) },
            accentOf = { priority -> priority.accent.container },
        )

        SegmentedSelector(
            label = stringResource(R.string.tasks_field_status),
            options = TaskStatus.entries,
            selected = draft.status,
            optionLabel = { status -> stringResource(status.labelRes) },
            onSelect = { status -> draft = draft.copy(status = status) },
        )

        DateField(
            label = stringResource(R.string.tasks_field_due),
            value = draft.dueDate,
            today = today,
            onChange = { date -> draft = draft.copy(dueDate = date) },
        )

        LabeledTextField(
            value = draft.project.orEmpty(),
            // Stored exactly as typed. Rewriting a whitespace-only value to null on
            // every keystroke fought the person typing: the space that separates two
            // words vanished as it was entered. The repository trims and turns an
            // empty name into null when the task is saved.
            onValueChange = { text -> draft = draft.copy(project = text) },
            label = stringResource(R.string.tasks_field_project),
            placeholder = stringResource(R.string.tasks_field_project_hint),
        )
        ProjectSuggestions(
            projects = projects,
            current = draft.project?.trim(),
            onPick = { project -> draft = draft.copy(project = project) },
        )

        LabeledTextField(
            value = draft.notes,
            onValueChange = { text -> draft = draft.copy(notes = text) },
            label = stringResource(R.string.tasks_field_notes),
            minLines = 4,
            // Notes are prose, so Enter has to insert a line break; a Done key here
            // would make paragraphs impossible.
            imeAction = ImeAction.Default,
        )

        if (meetingTitle != null) {
            QuietChip(
                text = stringResource(R.string.tasks_from_meeting, meetingTitle),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    if (askDelete) {
        ConfirmDialog(
            title = stringResource(R.string.tasks_delete_confirm_title),
            body = stringResource(R.string.tasks_delete_confirm_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                askDelete = false
                onDelete()
            },
            onDismiss = { askDelete = false },
        )
    }
}

/**
 * The projects already in use, one tap away.
 *
 * Typing a project by hand is how you end up with "Onboarding" and "onboarding"
 * as two separate groups, so every existing name is offered as a chip. The one
 * already selected is left out: it is visible in the field directly above.
 */
@Composable
private fun ProjectSuggestions(
    projects: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = projects.filter { it != current }
    if (options.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.tasks_field_project_recent),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            options.forEach { project ->
                // A real chip rather than a clickable label: it carries its own
                // button semantics, ripple and 48dp touch target, none of which a
                // bare Text with a clickable modifier had.
                SuggestionChip(
                    onClick = { onPick(project) },
                    label = { Text(text = project, maxLines = 1) },
                )
            }
        }
    }
}
