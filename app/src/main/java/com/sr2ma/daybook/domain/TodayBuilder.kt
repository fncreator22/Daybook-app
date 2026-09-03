package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Everything the Today screen shows, already bucketed.
 *
 * Sections render in declaration order and empty ones are skipped, so the screen
 * has no branching logic of its own.
 */
data class TodayBoard(
    val day: LocalDate,
    val overdue: List<Task> = emptyList(),
    val dueToday: List<Task> = emptyList(),
    val inProgress: List<Task> = emptyList(),
    val meetings: List<Meeting> = emptyList(),
    val followUps: List<Meeting> = emptyList(),
    val log: List<LogEntry> = emptyList(),
    val completedToday: Int = 0,
) {
    /** True when there is genuinely nothing to show, so the empty state can take over. */
    val isEmpty: Boolean
        get() = overdue.isEmpty() && dueToday.isEmpty() && inProgress.isEmpty() &&
            meetings.isEmpty() && followUps.isEmpty() && log.isEmpty()

    /** Count of things still asking for attention, for the tab badge. */
    val openCount: Int get() = overdue.size + dueToday.size + followUps.size
}

/**
 * Turns the three stored lists into a single day view.
 *
 * Pure by design: [today] and [zone] are passed in rather than read from the clock
 * inside, which is what makes the buckets testable without touching the device date.
 * [zone] only matters for deciding which calendar day a completion timestamp fell on.
 */
object TodayBuilder {

    fun build(
        tasks: List<Task>,
        meetings: List<Meeting>,
        logEntries: List<LogEntry>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): TodayBoard {
        val overdue = mutableListOf<Task>()
        val dueToday = mutableListOf<Task>()
        val inProgress = mutableListOf<Task>()
        var completedToday = 0

        tasks.forEach { task ->
            when {
                task.status == TaskStatus.DONE -> {
                    if (task.completedAt != null &&
                        Dates.localDateOf(task.completedAt, zone) == today
                    ) {
                        completedToday++
                    }
                }

                task.status == TaskStatus.CANCELLED -> Unit

                task.isOverdue(today) -> overdue += task

                task.isDueOn(today) -> dueToday += task

                // Started but not due today: worth surfacing so it is not forgotten,
                // and only once, which is why this branch comes last.
                task.status == TaskStatus.IN_PROGRESS -> inProgress += task
            }
        }

        return TodayBoard(
            day = today,
            // Longest overdue first, then the loudest priority within a date.
            overdue = overdue.sortedWith(
                compareBy<Task> { it.dueDate ?: LocalDate.MAX }
                    .thenByDescending { it.priority.storedValue },
            ),
            dueToday = dueToday.sortedWith(
                compareByDescending<Task> { it.priority.storedValue }
                    .thenBy { it.createdAt },
            ),
            inProgress = inProgress.sortedWith(
                compareByDescending<Task> { it.priority.storedValue }
                    .thenBy { it.dueDate ?: LocalDate.MAX },
            ),
            // Earliest meeting first; untimed ones sink to the bottom of the day.
            meetings = meetings.filter { it.day == today }.sortedWith(
                compareBy(nullsLast<LocalTime>()) { meeting: Meeting -> meeting.startTime },
            ),
            followUps = meetings.filter { it.needsFollowUpBy(today) }.sortedWith(
                compareBy<Meeting> { it.nextTouch ?: LocalDate.MAX }.thenBy { it.day },
            ),
            log = logEntries.filter { it.day == today }.sortedByDescending { it.createdAt },
            completedToday = completedToday,
        )
    }
}
