package com.sr2ma.daybook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.MeetingFilter
import com.sr2ma.daybook.ui.DaybookUiState
import com.sr2ma.daybook.ui.DaybookViewModel
import com.sr2ma.daybook.ui.components.EmptyState
import com.sr2ma.daybook.ui.components.FilterChipRow
import com.sr2ma.daybook.ui.components.MeetingRow
import com.sr2ma.daybook.ui.components.SearchField
import com.sr2ma.daybook.ui.labelRes
import com.sr2ma.daybook.sync.PendingSyncBanner

/**
 * Meetings, in whichever direction the filter is pointing.
 *
 * The sort order changes with the filter rather than being a separate control:
 * looking forward you want the soonest first, looking back you want the most
 * recent first, and nobody has ever wanted either list in the other order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsScreen(
    state: DaybookUiState,
    viewModel: DaybookViewModel,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Upcoming and Past are sorted in opposite directions, so a scroll position
    // carried across a filter change points at something unrelated — and on a
    // shorter list it lands past the end, which reads as an empty screen. Not
    // keyed on the meetings: saving one must not move the reader.
    LaunchedEffect(state.meetingFilter, state.meetingSearch) {
        listState.scrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.meetings_title)) })

        FilterChipRow(
            options = MeetingFilter.entries,
            selected = state.meetingFilter,
            label = { stringResource(it.labelRes) },
            onSelect = viewModel::setMeetingFilter,
        )

        SearchField(
            value = state.meetingSearch,
            onValueChange = viewModel::setMeetingSearch,
            placeholder = stringResource(R.string.meetings_search_hint),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        PendingSyncBanner(pendingCount = state.pendingSyncCount)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.visibleMeetings.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_meetings,
                        title = stringResource(R.string.meetings_empty_title),
                        body = stringResource(R.string.meetings_empty_body),
                    )
                }
            } else {
                items(items = state.visibleMeetings, key = { it.id }) { meeting ->
                    MeetingRow(
                        meeting = meeting,
                        today = state.today,
                        onClick = { viewModel.editMeeting(meeting) },
                        onToggleFollowUp = { viewModel.toggleFollowUpDone(meeting) },
                    )
                }
            }
        }
    }
}
