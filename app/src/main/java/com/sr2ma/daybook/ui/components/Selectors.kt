package com.sr2ma.daybook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R

/**
 * Controls that are shared between the list screens and the editor sheets.
 *
 * These sit here rather than in the screens that use them because the Tasks, Log
 * and Meetings screens each need the same search field and the same labelled
 * chip group, and three copies would drift apart.
 */

/**
 * The search field used by Tasks, Log and Meetings.
 *
 * Searching is instant, so there is no submit action; the keyboard shows a Search
 * key only to make that obvious, and pressing it just puts the keyboard away. The
 * clear button appears once there is something to clear, which is faster than
 * backspacing a long query.
 *
 * The text shown is held here rather than read back out of [value]. Going through
 * the ViewModel and waiting for the state flow to come back is a round trip that
 * does not complete within the frame, so two keystrokes inside one frame would
 * both be applied to the same stale [value] and the first character would be
 * lost. Every edit is still pushed straight out, so the filtering is unchanged.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(value) }
    // The last text handed to [onValueChange]. An incoming [value] equal to it is
    // this field's own edit arriving back and is ignored; anything else was set
    // somewhere other than here — the ViewModel clearing the search, or a state
    // restore landing on a ViewModel that has none — and wins.
    var pushed by rememberSaveable { mutableStateOf(value) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value) {
        if (value != pushed) {
            text = value
            pushed = value
        }
    }

    val edit: (String) -> Unit = { edited ->
        text = edited
        pushed = edited
        onValueChange(edited)
    }

    OutlinedTextField(
        value = text,
        onValueChange = edit,
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // There is nothing to submit, so Search only dismisses the keyboard.
        // Clearing focus ends the text input session, which is what hides the IME.
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { edit("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_clear),
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A labelled group of single-select chips, for the enum fields in the editors.
 *
 * It reuses [FilterChipRow] with zero content padding so the chips line up with
 * the text fields above and below them rather than sitting inset from everything
 * else in the sheet.
 */
@Composable
fun <T> SegmentedSelector(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accentOf: (T) -> Color? = { null },
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        FilterChipRow(
            options = options,
            selected = selected,
            label = optionLabel,
            onSelect = onSelect,
            accentOf = accentOf,
            contentPadding = PaddingValues(0.dp),
        )
    }
}

/**
 * A menu row that shows whether its option is the active one.
 *
 * The tick occupies its own fixed-width slot whether or not it is drawn, so the
 * labels in a menu stay aligned instead of shifting as the selection moves.
 */
@Composable
fun MenuCheckItem(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = {
            Box(modifier = Modifier.size(24.dp)) {
                if (checked) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    )
}

/**
 * The top row of an editor sheet: cancel, what is being edited, delete, save.
 *
 * Save is a text button rather than a tick icon because it is the one action in
 * the sheet that commits, and a word is unambiguous. [onDelete] is null for a
 * row that does not exist yet, which is what hides the delete button.
 */
@Composable
fun SheetHeader(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.action_cancel),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        TextButton(onClick = onSave, enabled = saveEnabled) {
            Text(stringResource(R.string.action_save))
        }
    }
}
