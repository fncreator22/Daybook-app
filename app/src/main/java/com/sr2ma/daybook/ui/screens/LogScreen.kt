package com.sr2ma.daybook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.sr2ma.daybook.ui.components.TimelineItemRow
import com.sr2ma.daybook.ui.theme.EmeraldTeal
import com.sr2ma.daybook.ui.theme.CoralRed
import com.sr2ma.daybook.ui.theme.ElectricBlue
import com.sr2ma.daybook.ui.theme.AmethystPurple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.ui.DaybookUiState
import com.sr2ma.daybook.ui.DaybookViewModel
import com.sr2ma.daybook.ui.accent
import com.sr2ma.daybook.ui.components.EmptyState
import com.sr2ma.daybook.ui.components.FilterChipRow
import com.sr2ma.daybook.ui.components.LogEntryRow
import com.sr2ma.daybook.ui.components.SearchField
import com.sr2ma.daybook.ui.components.SectionHeader
import com.sr2ma.daybook.ui.dayLabel
import com.sr2ma.daybook.ui.logKindLabel

/**
 * The daily log, newest day first.
 *
 * Entries are grouped by the day they belong to rather than shown as one flat
 * list, because the question being asked of the log is almost always "what
 * happened on that day", not "what is the hundredth most recent thing I wrote".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    state: DaybookUiState,
    viewModel: DaybookViewModel,
    modifier: Modifier = Modifier,
) {
    // Null leads the list and means "all kinds", so the filter row has an explicit
    // way back to unfiltered instead of relying on tapping the active chip again.
    val kinds = remember { listOf<LogKind?>(null) + LogKind.entries }
    val listState = rememberLazyListState()
    // A kind or search change replaces the days on screen, so a scroll position
    // from the old list is meaningless and can leave a short list looking empty.
    // Not keyed on the entries: writing one must not move the reader.
    LaunchedEffect(state.logKind, state.logSearch) {
        listState.scrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.log_title)) },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        )

        FilterChipRow(
            options = kinds,
            selected = state.logKind,
            label = { logKindLabel(it) },
            onSelect = viewModel::setLogKind,
            accentOf = { it?.accent?.container },
        )

        SearchField(
            value = state.logSearch,
            onValueChange = viewModel::setLogSearch,
            placeholder = stringResource(R.string.log_search_hint),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.logDays.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_log,
                        title = stringResource(R.string.log_empty_title),
                        body = stringResource(R.string.log_empty_body),
                    )
                }
            } else {
                state.logDays.forEach { logDay ->
                    item(key = "day-${logDay.day}") {
                        SectionHeader(
                            title = dayLabel(logDay.day, state.today),
                            count = logDay.entries.size,
                        )
                    }
                    itemsIndexed(items = logDay.entries, key = { _, entry -> entry.id }) { index, entry ->
                        val isFirst = index == 0
                        val isLast = index == logDay.entries.lastIndex
                        val dotColor = when (entry.kind) {
                            LogKind.WIN -> EmeraldTeal
                            LogKind.BLOCKER -> CoralRed
                            LogKind.DECISION -> ElectricBlue
                            LogKind.NOTE -> AmethystPurple
                        }
                        TimelineItemRow(
                            timeLabel = logKindLabel(entry.kind),
                            isFirst = isFirst,
                            isLast = isLast,
                            isActive = (entry.kind == LogKind.WIN),
                            nodeColor = dotColor,
                        ) {
                            LogEntryRow(entry = entry, onClick = { viewModel.editLogEntry(entry) })
                        }
                    }
                }
            }
        }
    }
}
