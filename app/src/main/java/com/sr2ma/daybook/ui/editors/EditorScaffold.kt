package com.sr2ma.daybook.ui.editors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.ui.components.SheetHeader

/**
 * The frame every editor shares: header, divider, scrolling fields.
 *
 * This is a plain full-screen surface rather than a `Dialog` or a
 * `ModalBottomSheet`. All three editors can open the Material date picker, which
 * is itself a dialog, and stacking one dialog on another — or squeezing a date
 * picker into a bottom sheet — is cramped and behaves differently across
 * versions. Drawn as a sibling of the app's Scaffold instead, it takes the whole
 * window, keeps the insets it is given, and only has to handle its own back
 * press.
 *
 * [onDelete] is null for a row that does not exist yet, which is what hides the
 * delete button.
 */
@Composable
fun EditorScaffold(
    title: String,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Back closes the editor exactly as the header's cancel button does: both call
    // onDismiss, and both discard the draft without asking. That is deliberate —
    // an editor is opened from a list, the draft only lives in composition, and
    // nothing has been written yet. Without this, back would leave the app instead.
    BackHandler(onBack = onDismiss)

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The activity is edge-to-edge and the Scaffold's inset consumption
                // does not reach this far, so the padding is applied here. safeDrawing
                // already includes the keyboard inset, so no separate imePadding.
                .safeDrawingPadding(),
        ) {
            SheetHeader(
                title = title,
                onDismiss = onDismiss,
                onSave = onSave,
                saveEnabled = saveEnabled,
                onDelete = onDelete,
            )
            HorizontalDivider()
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                content = content,
            )
        }
    }
}
