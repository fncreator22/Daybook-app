package com.sr2ma.daybook.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.ui.dayLabel
import java.time.LocalDate
import java.time.LocalTime

/** The single text input used by every editor, so labels and errors look alike. */
@Composable
fun LabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 1,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    // A local copy, so the semantics lambda below closes over a value the compiler
    // knows is non-null and the two uses cannot disagree.
    val message = error
    val errorSemantics = if (message == null) {
        Modifier
    } else {
        // Announced as *this field's* error rather than read out as a stray line
        // of text somewhere after it.
        Modifier.semantics { error(message) }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        // One decision, not two that can contradict each other: a field that is
        // allowed to grow is by definition not single-line.
        singleLine = minLines == 1,
        minLines = minLines,
        isError = message != null,
        // The message goes in the field's own supporting slot, so it is tied to
        // the field for a screen reader and reserves its space in the layout.
        supportingText = message?.let { { Text(it) } },
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = imeAction,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(errorSemantics),
    )
}

/**
 * A read-only field that opens the Material date picker.
 *
 * The whole field is the target, not just the icon: a field you cannot type into
 * looks exactly like a button, so it should behave like one. [clearable] is false
 * for the two dates the data model requires — a log entry's day and a meeting's
 * day — where offering a clear button would promise something the editor then has
 * to undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    today: LocalDate,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true,
) {
    var showPicker by remember { mutableStateOf(false) }

    PickerField(
        label = label,
        text = dayLabel(value, today),
        icon = R.drawable.ic_calendar,
        pickLabel = stringResource(R.string.date_pick),
        onOpen = { showPicker = true },
        onClear = if (clearable && value != null) ({ onChange(null) }) else null,
        modifier = modifier,
    )

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Dates.toUtcMillis(value ?: today),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            // "OK", not "Save": confirming a picker chooses a date, it does not
            // commit the editor, and the Save button is still up in the header.
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onChange(Dates.fromUtcMillis(it)) }
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * A read-only field that opens a Material time picker, mirroring [DateField].
 *
 * This used to be a free-text "HH:mm" field with a number keyboard, which on a
 * real phone has no colon key: the hint asked for something the keyboard could not
 * type, and a half-typed time silently failed to save because the change was only
 * reported for text that parsed. The picker cannot produce an invalid time, so
 * there is nothing left to validate.
 *
 * A meeting may legitimately have no time, so the clear button stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    value: LocalTime?,
    onChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickLabel = stringResource(R.string.time_pick)

    PickerField(
        label = label,
        text = value?.let { Dates.timeLabel(it) } ?: stringResource(R.string.time_none),
        icon = R.drawable.ic_clock,
        pickLabel = pickLabel,
        onOpen = { showPicker = true },
        onClear = if (value == null) null else ({ onChange(null) }),
        modifier = modifier,
    )

    if (showPicker) {
        // 24-hour, because every stored and displayed time in the app is "HH:mm".
        val state = rememberTimePickerState(
            initialHour = value?.hour ?: DEFAULT_MEETING_HOUR,
            initialMinute = value?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(pickLabel) },
            // TimeInput rather than the dial: a meeting time is usually already
            // known, and two number boxes fit an alert dialog's width where the
            // 256dp dial does not.
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(LocalTime.of(state.hour, state.minute))
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Where the time picker starts when a meeting has no time yet. */
private const val DEFAULT_MEETING_HOUR = 9

/**
 * The shared body of [DateField] and [TimeField]: a field you tap rather than type
 * into.
 *
 * The tap target is a transparent overlay rather than a click on the text field,
 * because a `readOnly` text field still consumes the press itself — only
 * `enabled = false` would give it up, and that would also grey out the label and
 * kill the trailing buttons. The overlay is inset by one icon-button width per
 * trailing button so that "clear" is still reachable, and draws no indication of
 * its own: a rectangular ripple over an outlined field looks like a bug.
 */
@Composable
private fun PickerField(
    label: String,
    text: String,
    @DrawableRes icon: Int,
    pickLabel: String,
    onOpen: () -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val clearLabel = stringResource(R.string.action_clear)
    // One IconButton is 48dp of touch target; two when the value can be cleared.
    val trailingInset = if (onClear == null) 48.dp else 96.dp

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            trailingIcon = {
                Row {
                    if (onClear != null) {
                        IconButton(onClick = onClear) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = clearLabel,
                            )
                        }
                    }
                    IconButton(onClick = onOpen) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = pickLabel,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(end = trailingInset)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = pickLabel,
                    onClick = onOpen,
                ),
        )
    }
}
