package com.sr2ma.daybook.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.LogDay
import com.sr2ma.daybook.domain.MeetingFilter
import com.sr2ma.daybook.domain.TaskFilter
import com.sr2ma.daybook.domain.TaskGroup
import com.sr2ma.daybook.domain.TaskSort
import com.sr2ma.daybook.domain.TodayBoard
import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.Task
import java.time.LocalDate

/** The six destinations in the bottom bar. */
enum class DaybookTab(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    TODAY(R.string.nav_today, R.drawable.ic_today),
    TASKS(R.string.nav_tasks, R.drawable.ic_tasks),
    LOG(R.string.nav_log, R.drawable.ic_log),
    MEETINGS(R.string.nav_meetings, R.drawable.ic_meetings),
    WALLET(R.string.nav_wallet, R.drawable.ic_wallet),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
}

/**
 * Which editor sheet is open, if any.
 *
 * This lives in the ViewModel rather than in a screen so that opening the task
 * editor from the Today screen, the Tasks screen or a meeting's action items is
 * one code path, and so the sheet survives the process being recreated.
 */
sealed interface Editor {
    /** [seed] is a blank task for "new", or the existing row for "edit". */
    data class TaskSheet(val seed: Task) : Editor

    data class LogSheet(val seed: LogEntry) : Editor

    data class MeetingSheet(val seed: Meeting) : Editor

    /** [seed] is a Pass pre-populated from a scan, or the existing row for "edit". */
    data class PassSheet(val seed: Pass) : Editor
}

/**
 * A one-shot message for the snackbar.
 *
 * The text is carried as a resource id plus format arguments rather than as a
 * finished string, because the ViewModel has no Context to resolve one with;
 * the screen calls `stringResource(textRes, *args)` when it shows the snackbar.
 *
 * [id] exists so that showing the same message twice in a row still counts as
 * two messages; without it the second would be swallowed as "no state change".
 */
data class UserMessage(
    val id: Long,
    @param:StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
)

/**
 * Everything on screen, in one value.
 *
 * The raw lists and the derived views are both here: derivation happens once in
 * the ViewModel when the data or a filter changes, so recomposition never
 * re-sorts a list.
 */
data class DaybookUiState(
    val tab: DaybookTab = DaybookTab.TODAY,
    val today: LocalDate = LocalDate.EPOCH,
    val loaded: Boolean = false,

    // Raw data, straight from the repository.
    val tasks: List<Task> = emptyList(),
    val logEntries: List<LogEntry> = emptyList(),
    val meetings: List<Meeting> = emptyList(),
    val projects: List<String> = emptyList(),
    val passes: List<Pass> = emptyList(),

    // Wallet screen: true while the camera scanner is showing.
    val walletScanOpen: Boolean = false,

    // Tasks screen controls.
    val taskFilter: TaskFilter = TaskFilter.OPEN,
    val taskSort: TaskSort = TaskSort.PRIORITY,
    val taskProject: String? = null,
    val taskSearch: String = "",
    val groupByProject: Boolean = false,

    // Log screen controls.
    val logKind: LogKind? = null,
    val logSearch: String = "",

    // Meetings screen controls.
    val meetingFilter: MeetingFilter = MeetingFilter.UPCOMING,
    val meetingSearch: String = "",

    // Derived views, recomputed by the ViewModel.
    val board: TodayBoard = TodayBoard(day = LocalDate.EPOCH),
    val visibleTasks: List<Task> = emptyList(),
    val taskGroups: List<TaskGroup> = emptyList(),
    val logDays: List<LogDay> = emptyList(),
    val visibleMeetings: List<Meeting> = emptyList(),

    // Derived for whichever editor is open, and empty otherwise. These are here
    // rather than resolved by the editor itself so that no scan of the task or
    // meeting list happens during composition.
    val editorActionItems: List<Task> = emptyList(),
    val editorMeetingTitle: String? = null,

    val editor: Editor? = null,
    val message: UserMessage? = null,
    val busy: Boolean = false,
) {
    val taskCount: Int get() = tasks.size
    val logCount: Int get() = logEntries.size
    val meetingCount: Int get() = meetings.size
}
