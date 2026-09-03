package com.sr2ma.daybook.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.MeetingFilter
import com.sr2ma.daybook.domain.TaskFilter
import com.sr2ma.daybook.domain.TaskSort
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.TaskStatus
import com.sr2ma.daybook.ui.theme.Accent
import com.sr2ma.daybook.ui.theme.DaybookAccents
import java.time.LocalDate

// Enum-to-resource and enum-to-colour mappings live here so no screen has to
// grow a `when` over the domain enums, and adding a value fails in one place.

@get:StringRes
val Priority.labelRes: Int
    get() = when (this) {
        Priority.LOW -> R.string.priority_low
        Priority.MEDIUM -> R.string.priority_medium
        Priority.HIGH -> R.string.priority_high
        Priority.URGENT -> R.string.priority_urgent
    }

val Priority.accent: Accent
    get() = when (this) {
        Priority.LOW -> DaybookAccents.priorityLow
        Priority.MEDIUM -> DaybookAccents.priorityMedium
        Priority.HIGH -> DaybookAccents.priorityHigh
        Priority.URGENT -> DaybookAccents.priorityUrgent
    }

@get:StringRes
val TaskStatus.labelRes: Int
    get() = when (this) {
        TaskStatus.OPEN -> R.string.status_open
        TaskStatus.IN_PROGRESS -> R.string.status_in_progress
        TaskStatus.DONE -> R.string.status_done
        TaskStatus.CANCELLED -> R.string.status_cancelled
    }

@get:StringRes
val LogKind.labelRes: Int
    get() = when (this) {
        LogKind.NOTE -> R.string.kind_note
        LogKind.DECISION -> R.string.kind_decision
        LogKind.BLOCKER -> R.string.kind_blocker
        LogKind.WIN -> R.string.kind_win
    }

val LogKind.accent: Accent
    get() = when (this) {
        LogKind.NOTE -> DaybookAccents.kindNote
        LogKind.DECISION -> DaybookAccents.kindDecision
        LogKind.BLOCKER -> DaybookAccents.kindBlocker
        LogKind.WIN -> DaybookAccents.kindWin
    }

@get:StringRes
val TaskFilter.labelRes: Int
    get() = when (this) {
        TaskFilter.ALL -> R.string.tasks_filter_all
        TaskFilter.OPEN -> R.string.tasks_filter_open
        TaskFilter.TODAY -> R.string.tasks_filter_today
        TaskFilter.OVERDUE -> R.string.tasks_filter_overdue
        TaskFilter.DONE -> R.string.tasks_filter_done
    }

@get:StringRes
val TaskSort.labelRes: Int
    get() = when (this) {
        TaskSort.PRIORITY -> R.string.tasks_sort_priority
        TaskSort.DUE_DATE -> R.string.tasks_sort_due
        TaskSort.NEWEST -> R.string.tasks_sort_newest
    }

@get:StringRes
val MeetingFilter.labelRes: Int
    get() = when (this) {
        MeetingFilter.UPCOMING -> R.string.meetings_filter_upcoming
        MeetingFilter.PAST -> R.string.meetings_filter_past
        MeetingFilter.NEEDS_FOLLOW_UP -> R.string.meetings_filter_follow_up
    }

/** "All kinds" plus one entry per [LogKind], for the log filter row. */
@Composable
fun logKindLabel(kind: LogKind?): String =
    stringResource(kind?.labelRes ?: R.string.log_filter_all_kinds)

/**
 * A date written the way a person would say it: "Today", "Tomorrow",
 * "Yesterday", then "3 Sep", and "3 Sep 2025" once the year differs.
 */
@Composable
fun dayLabel(date: LocalDate?, today: LocalDate): String = when (date) {
    null -> stringResource(R.string.date_none)
    today -> stringResource(R.string.date_today)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> Dates.shortLabel(date, today)
}

/** "1 day late" / "4 days late", or null when [date] is not in the past. */
@Composable
fun latenessLabel(date: LocalDate?, today: LocalDate): String? {
    if (date == null || !date.isBefore(today)) return null
    val days = Dates.daysBetween(date, today)
    return if (days == 1L) {
        stringResource(R.string.date_overdue_by_one)
    } else {
        stringResource(R.string.date_overdue_by, days.toInt())
    }
}
