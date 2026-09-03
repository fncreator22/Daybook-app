package com.sr2ma.daybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.ui.theme.Accent

/**
 * A small tinted pill. Used for priorities, log kinds, projects and dates.
 *
 * The whole point of taking an [Accent] rather than two loose colours is that the
 * text can never be paired with the wrong tint behind it.
 */
@Composable
fun AccentChip(
    text: String,
    accent: Accent,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = accent.onContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(accent.container, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** An untinted pill, for quieter metadata like a project name. */
@Composable
fun QuietChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * A horizontally scrolling row of single-select filter chips.
 *
 * Generic over the option type so the Tasks, Log and Meetings screens all use
 * the same control instead of three near-identical copies.
 *
 * [contentPadding] is inside the scroll, so the first and last chip clear the
 * screen edge without the padding itself becoming an unscrollable gutter. Editor
 * sheets pass zero, because the sheet already provides its own margin.
 */
@Composable
fun <T> FilterChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accentOf: (T) -> Color? = { null },
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val tint = accentOf(option)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = MaterialTheme.shapes.small,
                colors = if (tint != null) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tint,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                },
            )
        }
    }
}
