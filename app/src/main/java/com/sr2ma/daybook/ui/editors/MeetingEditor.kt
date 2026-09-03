package com.sr2ma.daybook.ui.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import com.sr2ma.daybook.ui.components.ConfirmDialog
import com.sr2ma.daybook.ui.components.DateField
import com.sr2ma.daybook.ui.components.LabeledTextField
import com.sr2ma.daybook.ui.components.SectionHeader
import com.sr2ma.daybook.ui.components.TimeField
import java.time.LocalDate

/**
 * A meeting and the commitments that came out of it.
 *
 * The action items at the bottom are real tasks, not a list owned by the meeting,
 * which is why they save the moment they are added instead of waiting for the
 * Save button: the meeting draft and the tasks are separate rows in separate
 * tables, and pretending otherwise would mean losing typed action items whenever
 * someone backed out of the sheet.
 */
@Composable
fun MeetingEditor(
    seed: Meeting,
    today: LocalDate,
    actionItems: List<Task>,
    onSave: (Meeting) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onAddActionItem: (String) -> Unit,
    onToggleActionItem: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the id rather than on the whole seed: adding an action item writes to
    // the database, the repository re-emits, and a value key would take that as a
    // different meeting and discard everything typed into this sheet so far.
    var draft by remember(seed.id) { mutableStateOf(seed) }
    var titleTouched by remember(seed.id) { mutableStateOf(false) }
    var askDelete by remember(seed.id) { mutableStateOf(false) }

    val isNew = seed.id == 0L
    val titleError = if (titleTouched && draft.title.isBlank()) {
        stringResource(R.string.error_title_required)
    } else {
        null
    }

    EditorScaffold(
        title = stringResource(if (isNew) R.string.meetings_new else R.string.meetings_edit),
        saveEnabled = draft.title.isNotBlank(),
        onSave = { onSave(draft) },
        onDismiss = onDismiss,
        onDelete = if (isNew) null else ({ askDelete = true }),
        modifier = modifier,
    ) {
        LabeledTextField(
            value = draft.title,
            onValueChange = { text ->
                titleTouched = true
                draft = draft.copy(title = text)
            },
            label = stringResource(R.string.meetings_field_title),
            error = titleError,
        )

        LabeledTextField(
            value = draft.attendees,
            onValueChange = { text -> draft = draft.copy(attendees = text) },
            label = stringResource(R.string.meetings_field_attendees),
            placeholder = stringResource(R.string.meetings_field_attendees_hint),
        )

        DateField(
            label = stringResource(R.string.meetings_field_day),
            value = draft.day,
            today = today,
            // A meeting happens on a day by definition, so there is no clear button
            // and an empty picker keeps whatever was already set.
            clearable = false,
            onChange = { date -> draft = draft.copy(day = date ?: draft.day) },
        )

        TimeField(
            label = stringResource(R.string.meetings_field_time),
            value = draft.startTime,
            onChange = { time -> draft = draft.copy(startTime = time) },
        )

        LabeledTextField(
            value = draft.location,
            onValueChange = { text -> draft = draft.copy(location = text) },
            label = stringResource(R.string.meetings_field_location),
            placeholder = stringResource(R.string.meetings_field_location_hint),
        )

        LabeledTextField(
            value = draft.notes,
            onValueChange = { text -> draft = draft.copy(notes = text) },
            label = stringResource(R.string.meetings_field_notes),
            minLines = 5,
            imeAction = ImeAction.Default,
        )

        DateField(
            label = stringResource(R.string.meetings_field_next_touch),
            value = draft.nextTouch,
            today = today,
            onChange = { date ->
                // Clearing the date clears the tick with it: "done" has nothing to
                // refer to once there is no follow-up to be done.
                draft = draft.copy(
                    nextTouch = date,
                    followUpDone = if (date == null) false else draft.followUpDone,
                )
            },
        )
        // The tick is only offered once there is a follow-up to tick off.
        if (draft.nextTouch != null) {
            CheckRow(
                label = stringResource(R.string.meetings_follow_up_done),
                checked = draft.followUpDone,
                onCheckedChange = { done -> draft = draft.copy(followUpDone = done) },
            )
        }

        // No inset passed: this column is padded already, and SectionHeader no
        // longer carries a horizontal inset of its own.
        SectionHeader(title = stringResource(R.string.meetings_action_items))
        if (isNew) {
            // A task points at its meeting by id, and the id does not exist until
            // the meeting is saved, so there is nothing to attach to yet.
            Text(
                text = stringResource(R.string.meetings_action_items_after_save),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (actionItems.isEmpty()) {
                Text(
                    text = stringResource(R.string.meetings_no_action_items),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                actionItems.forEach { task ->
                    CheckRow(
                        label = task.title,
                        checked = task.status == TaskStatus.DONE,
                        onCheckedChange = { onToggleActionItem(task) },
                        strikeWhenChecked = true,
                    )
                }
            }
            ActionItemField(onAdd = onAddActionItem)
        }
    }

    if (askDelete) {
        ConfirmDialog(
            title = stringResource(R.string.meetings_delete_confirm_title),
            body = stringResource(R.string.meetings_delete_confirm_body),
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
 * A checkbox and its label, tappable across the whole row.
 *
 * The row rather than the box carries the toggle, which gives a full-width hit
 * area and — because [Modifier.toggleable] merges the label into one accessible
 * node — makes a screen reader announce "Follow-up done, checked" instead of an
 * unnamed checkbox. That is also why the box itself is passed a null callback.
 *
 * [strikeWhenChecked] is what distinguishes a finished task from a setting that
 * happens to be switched on.
 */
@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    strikeWhenChecked: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            // An action item is a row you tap with a thumb, so it gets the Material
            // minimum target height even when its label is one short line.
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            ),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (checked && strikeWhenChecked) {
                TextDecoration.LineThrough
            } else {
                null
            },
        )
    }
}

/**
 * One line in, one task out, attached to this meeting.
 *
 * Keeping this inside the sheet is the whole point: the moment to capture "I said
 * I would do that" is while the meeting notes are still open.
 */
@Composable
private fun ActionItemField(
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text.trim())
            text = ""
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { entered -> text = entered },
        placeholder = { Text(stringResource(R.string.meetings_action_item_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            if (text.isNotBlank()) {
                IconButton(onClick = submit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.meetings_add_action_item),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}
