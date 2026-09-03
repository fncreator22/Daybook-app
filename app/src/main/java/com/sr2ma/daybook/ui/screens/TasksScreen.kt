package com.sr2ma.daybook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.TaskFilter
import com.sr2ma.daybook.domain.TaskSort
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.ui.DaybookUiState
import com.sr2ma.daybook.ui.DaybookViewModel
import com.sr2ma.daybook.ui.components.EmptyState
import com.sr2ma.daybook.ui.components.FilterChipRow
import com.sr2ma.daybook.ui.components.MenuCheckItem
import com.sr2ma.daybook.ui.components.SearchField
import com.sr2ma.daybook.ui.components.SectionHeader
import com.sr2ma.daybook.ui.components.TaskRow
import com.sr2ma.daybook.ui.labelRes

/**
 * The full task list, with the three controls that matter kept visible and the
 * rarer ones folded into one menu.
 *
 * Filter and search are on screen because they are changed constantly; sort,
 * grouping and the project filter live in the overflow because they are set once
 * and then left alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    state: DaybookUiState,
    viewModel: DaybookViewModel,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // A filter, sort or search change swaps the whole list out from under the
    // scroll position, and being forty rows down a list that is now three rows
    // long looks like an empty screen. Deliberately not keyed on the tasks
    // themselves: adding one, or ticking one off, must not jump to the top.
    LaunchedEffect(
        state.taskFilter,
        state.taskSort,
        state.taskProject,
        state.taskSearch,
        state.groupByProject,
    ) {
        listState.scrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tasks_title)) },
            actions = { TaskListMenu(state = state, viewModel = viewModel) },
        )

        FilterChipRow(
            options = TaskFilter.entries,
            selected = state.taskFilter,
            label = { stringResource(it.labelRes) },
            onSelect = viewModel::setTaskFilter,
        )

        SearchField(
            value = state.taskSearch,
            onValueChange = viewModel::setTaskSearch,
            placeholder = stringResource(R.string.tasks_search_hint),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                // Grouping cannot change whether there is anything to show:
                // taskGroups is groupByProject applied to visibleTasks, so it is
                // empty for exactly the same lists visibleTasks is empty for.
                state.visibleTasks.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_tasks,
                        title = stringResource(R.string.tasks_empty_title),
                        body = stringResource(R.string.tasks_empty_body),
                    )
                }

                state.groupByProject -> state.taskGroups.forEach { group ->
                    // The project is already in the heading, so repeating it on each
                    // row inside the group would be noise.
                    projectGroup(
                        project = group.project,
                        tasks = group.tasks,
                        state = state,
                        viewModel = viewModel,
                    )
                }

                else -> items(
                    items = state.visibleTasks,
                    key = { it.id },
                ) { task ->
                    TaskRow(
                        task = task,
                        today = state.today,
                        onToggleDone = { viewModel.toggleTaskDone(task) },
                        onClick = { viewModel.editTask(task) },
                    )
                }
            }
        }
    }
}

/** One project heading and its tasks, when the list is grouped. */
private fun LazyListScope.projectGroup(
    project: String?,
    tasks: List<Task>,
    state: DaybookUiState,
    viewModel: DaybookViewModel,
) {
    // Null and blank both mean "no project", and a heading has to say something.
    val heading = project.orEmpty()
    item(key = "group-$heading") {
        SectionHeader(
            title = heading.ifBlank { stringResource(R.string.tasks_no_project) },
            count = tasks.size,
            // The list's contentPadding does not reach a header's own text, so the
            // inset is passed here rather than baked into SectionHeader.
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    items(items = tasks, key = { "$heading-${it.id}" }) { task ->
        TaskRow(
            task = task,
            today = state.today,
            onToggleDone = { viewModel.toggleTaskDone(task) },
            onClick = { viewModel.editTask(task) },
            showProject = false,
        )
    }
}

/**
 * Sort order, grouping and the project filter, in one overflow menu.
 *
 * The project entries are built from the projects that actually exist rather than
 * from a fixed list, so the menu can never offer a filter that matches nothing.
 */
@Composable
private fun TaskListMenu(
    state: DaybookUiState,
    viewModel: DaybookViewModel,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_sort),
                contentDescription = stringResource(R.string.action_more),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuSectionLabel(stringResource(R.string.tasks_sort_label))
            TaskSort.entries.forEach { sort ->
                MenuCheckItem(
                    text = stringResource(sort.labelRes),
                    checked = sort == state.taskSort,
                    onClick = {
                        viewModel.setTaskSort(sort)
                        expanded = false
                    },
                )
            }

            HorizontalDivider()
            MenuCheckItem(
                text = stringResource(R.string.tasks_group_by_project),
                checked = state.groupByProject,
                onClick = {
                    viewModel.toggleGroupByProject()
                    expanded = false
                },
            )

            if (state.projects.isNotEmpty()) {
                HorizontalDivider()
                MenuSectionLabel(stringResource(R.string.tasks_field_project))
                MenuCheckItem(
                    text = stringResource(R.string.tasks_all_projects),
                    checked = state.taskProject == null,
                    onClick = {
                        viewModel.setTaskProject(null)
                        expanded = false
                    },
                )
                state.projects.forEach { project ->
                    MenuCheckItem(
                        text = project,
                        checked = project == state.taskProject,
                        onClick = {
                            viewModel.setTaskProject(project)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * A heading inside a menu. Deliberately not a `DropdownMenuItem`: it is a label,
 * and making it tappable would invite taps that do nothing.
 */
@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}
