package com.sr2ma.daybook.ui.editors

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.PassCategory
import java.time.LocalDate

/**
 * Full-screen editor that appears after a scan (or when editing a saved pass).
 *
 * Per grilling Q3: always shown after scan â€” user must confirm before saving.
 * Per grilling Q4: category picker is always visible, pre-selected by heuristic.
 *
 * The barcode value and format are not editable â€” they are the factual record of
 * what the scanner decoded. The user edits the human-readable metadata only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PassConfirmSheet(
    seed: Pass,
    onSave: (Pass) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(seed.title) }
    var category by rememberSaveable { mutableStateOf(seed.category) }
    var notes by rememberSaveable { mutableStateOf(seed.notes) }
    var balance by rememberSaveable { mutableStateOf(seed.balance ?: "") }
    var expiryDate by remember { mutableStateOf(seed.expiryDate) }
    var titleError by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val isNew = seed.id == 0L
    val sheetTitle = stringResource(
        if (isNew) R.string.wallet_confirm_title else R.string.wallet_confirm_edit_title,
    )

    EditorScaffold(
        title = sheetTitle,
        saveEnabled = true,
        onSave = {
            if (title.isBlank()) { titleError = true; return@EditorScaffold }
            onSave(
                seed.copy(
                    title = title.trim(),
                    category = category,
                    notes = notes.trim(),
                    balance = balance.trim().takeIf { it.isNotEmpty() },
                    expiryDate = expiryDate,
                )
            )
        },
        onDismiss = onDismiss,
        onDelete = if (isNew) null else ({ showDeleteConfirm = true }),
    ) {
        // Name
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; titleError = false },
            label = { Text(stringResource(R.string.wallet_field_title)) },
            placeholder = { Text(stringResource(R.string.wallet_field_title_hint)) },
            isError = titleError,
            supportingText = if (titleError) {
                { Text(stringResource(R.string.error_title_required)) }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(4.dp))

        // Category picker (grilling Q4: always shown, pre-selected by heuristic)
        Text(
            text = stringResource(R.string.wallet_field_category),
            style = MaterialTheme.typography.labelMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PassCategory.entries.forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(categoryLabel(cat)) },
                )
            }
        }

        // Expiry date picker
        Text(
            text = stringResource(R.string.wallet_field_expiry),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = {
                    val now = expiryDate ?: LocalDate.now()
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> expiryDate = LocalDate.of(y, m + 1, d) },
                        now.year, now.monthValue - 1, now.dayOfMonth,
                    ).show()
                }
            ) {
                Text(
                    text = if (expiryDate != null) {
                        stringResource(
                            R.string.pass_expiry_set,
                            "${expiryDate!!.dayOfMonth} " +
                                expiryDate!!.month.name.lowercase()
                                    .replaceFirstChar { it.uppercase() } +
                                " ${expiryDate!!.year}",
                        )
                    } else {
                        stringResource(R.string.wallet_field_expiry) + ": tap to set"
                    }
                )
            }
            if (expiryDate != null) {
                TextButton(onClick = { expiryDate = null }) {
                    Text(stringResource(R.string.pass_expiry_clear))
                }
            }
        }

        // Balance (optional)
        OutlinedTextField(
            value = balance,
            onValueChange = { balance = it },
            label = { Text(stringResource(R.string.wallet_field_balance)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.wallet_field_notes)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        // Barcode value preview (read-only)
        val preview = seed.barcodeValue.take(30) + if (seed.barcodeValue.length > 30) "â€¦" else ""
        Text(
            text = stringResource(R.string.pass_barcode_preview, preview),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(R.string.wallet_barcode_format, seed.barcodeFormat),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.wallet_delete_confirm_title)) },
            text = { Text(stringResource(R.string.wallet_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun categoryLabel(category: PassCategory): String = stringResource(
    when (category) {
        PassCategory.LOYALTY_CARD  -> R.string.pass_cat_loyalty_card
        PassCategory.EVENT_TICKET  -> R.string.pass_cat_event_ticket
        PassCategory.TRANSPORT     -> R.string.pass_cat_transport
        PassCategory.GIFT_CARD     -> R.string.pass_cat_gift_card
        PassCategory.ID            -> R.string.pass_cat_id
        PassCategory.HEALTH        -> R.string.pass_cat_health
        PassCategory.OTHER         -> R.string.pass_cat_other
    },
)
