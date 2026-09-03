package com.sr2ma.daybook.ui.editors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.ui.accent
import com.sr2ma.daybook.ui.components.ConfirmDialog
import com.sr2ma.daybook.ui.components.DateField
import com.sr2ma.daybook.ui.components.LabeledTextField
import com.sr2ma.daybook.ui.components.SegmentedSelector
import com.sr2ma.daybook.ui.labelRes
import java.time.LocalDate

/**
 * One log entry: what happened, what kind of thing it was, and when.
 *
 * The body comes first and is the tall field, because the entry is the point and
 * the kind is a label you attach afterwards. Kind defaults to Note so writing
 * something down never requires a decision first.
 */
@Composable
fun LogEditor(
    seed: LogEntry,
    today: LocalDate,
    onSave: (LogEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the id rather than on the whole seed: the repository re-emits its
    // list after every write, and a value key would hand back a "new" equal seed
    // and wipe the draft mid-edit. A different entry still has a different id.
    var draft by remember(seed.id) { mutableStateOf(seed) }
    var bodyTouched by remember(seed.id) { mutableStateOf(false) }
    var askDelete by remember(seed.id) { mutableStateOf(false) }

    val isNew = seed.id == 0L
    val bodyError = if (bodyTouched && draft.body.isBlank()) {
        stringResource(R.string.log_body_required)
    } else {
        null
    }

    EditorScaffold(
        title = stringResource(if (isNew) R.string.log_new else R.string.log_edit),
        saveEnabled = draft.body.isNotBlank(),
        onSave = { onSave(draft) },
        onDismiss = onDismiss,
        onDelete = if (isNew) null else ({ askDelete = true }),
        modifier = modifier,
    ) {
        LabeledTextField(
            value = draft.body,
            onValueChange = { text ->
                bodyTouched = true
                draft = draft.copy(body = text)
            },
            label = stringResource(R.string.log_field_body),
            placeholder = stringResource(R.string.log_field_body_hint),
            minLines = 5,
            error = bodyError,
            imeAction = ImeAction.Default,
        )

        SegmentedSelector(
            label = stringResource(R.string.log_field_kind),
            options = LogKind.entries,
            selected = draft.kind,
            optionLabel = { kind -> stringResource(kind.labelRes) },
            onSelect = { kind -> draft = draft.copy(kind = kind) },
            accentOf = { kind -> kind.accent.container },
        )

        DateField(
            label = stringResource(R.string.log_field_day),
            value = draft.day,
            today = today,
            // An entry always belongs to some day, so no clear button is offered and
            // an empty result from the picker keeps whatever was already set.
            clearable = false,
            onChange = { date -> draft = draft.copy(day = date ?: draft.day) },
        )
    }

    if (askDelete) {
        ConfirmDialog(
            title = stringResource(R.string.log_delete_confirm_title),
            body = stringResource(R.string.log_delete_confirm_body),
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
