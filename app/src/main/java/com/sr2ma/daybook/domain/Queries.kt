package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime

/** The chips across the top of the Tasks screen. */
enum class TaskFilter {
    ALL,
    OPEN,
    TODAY,
    OVERDUE,
    DONE,
    ;

    fun matches(task: Task, today: LocalDate): Boolean = when (this) {
        ALL -> true
        OPEN -> task.isOpen
        // "Today" means anything you could reasonably close today: due today or
        // already late. Future work is deliberately excluded.
        TODAY -> task.isOpen && task.isDueBy(today)
        OVERDUE -> task.isOverdue(today)
        DONE -> task.status == TaskStatus.DONE
    }
}

/** The Tasks screen sort order. */
enum class TaskSort { PRIORITY, DUE_DATE, NEWEST }

/** One project heading plus its tasks, when the Tasks screen is grouped. */
data class TaskGroup(val project: String?, val tasks: List<Task>)

/**
 * Filtering, sorting and grouping for the Tasks screen.
 *
 * The DAO returns one canonical order; re-slicing in memory here avoids a query
 * per filter combination and keeps all of this logic unit-testable.
 */
object TaskQuery {

    fun apply(
        tasks: List<Task>,
        filter: TaskFilter,
        sort: TaskSort,
        today: LocalDate,
        project: String? = null,
        search: String = "",
    ): List<Task> {
        val needle = search.trim().lowercase()
        return tasks
            .asSequence()
            .filter { filter.matches(it, today) }
            .filter { project == null || it.project == project }
            .filter { needle.isEmpty() || it.matchesSearch(needle) }
            .sortedWith(comparatorFor(sort))
            .toList()
    }

    private fun Task.matchesSearch(lowercaseNeedle: String): Boolean =
        title.lowercase().contains(lowercaseNeedle) ||
            notes.lowercase().contains(lowercaseNeedle) ||
            (project?.lowercase()?.contains(lowercaseNeedle) == true)

    /**
     * Closed work sinks below open work in every order. Without that, ticking a
     * task off makes it jump around the list instead of settling at the bottom.
     */
    private fun comparatorFor(sort: TaskSort): Comparator<Task> {
        val closedLast = compareBy<Task> { if (it.status.isClosed) 1 else 0 }
        return when (sort) {
            TaskSort.PRIORITY -> closedLast
                .thenByDescending { it.priority.storedValue }
                .thenBy { it.dueDate ?: LocalDate.MAX }
                .thenByDescending { it.createdAt }

            TaskSort.DUE_DATE -> closedLast
                .thenBy { it.dueDate ?: LocalDate.MAX }
                .thenByDescending { it.priority.storedValue }
                .thenByDescending { it.createdAt }

            TaskSort.NEWEST -> closedLast
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id }
        }
    }

    /** Groups by project, keeping the incoming order inside each group. Unfiled comes last. */
    fun groupByProject(tasks: List<Task>): List<TaskGroup> =
        tasks.groupBy { it.project }
            .map { (project, grouped) -> TaskGroup(project, grouped) }
            .sortedWith(
                compareBy<TaskGroup> { if (it.project == null) 1 else 0 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.project.orEmpty() },
            )

    /**
     * Every task attached to [meetingId], for the meeting detail sheet. Closed items
     * stay in the list on purpose — the sheet is a record of what the meeting produced
     * — they just sink below the open ones, loudest priority first, oldest first.
     */
    fun forMeeting(tasks: List<Task>, meetingId: Long): List<Task> =
        tasks.filter { it.meetingId == meetingId }
            .sortedWith(
                compareBy<Task> { if (it.status.isClosed) 1 else 0 }
                    .thenByDescending { it.priority.storedValue }
                    .thenBy { it.createdAt },
            )
}

/** The chips across the top of the Meetings screen. */
enum class MeetingFilter {
    UPCOMING,
    PAST,
    NEEDS_FOLLOW_UP,
    ;

    fun matches(meeting: Meeting, today: LocalDate): Boolean = when (this) {
        UPCOMING -> !meeting.day.isBefore(today)
        PAST -> meeting.day.isBefore(today)
        // Due by today, deliberately the same test the Today screen's follow-up
        // section uses, so the chip and Today never disagree. A next touch dated
        // next week is not something you can act on yet.
        NEEDS_FOLLOW_UP -> meeting.needsFollowUpBy(today)
    }
}

object MeetingQuery {

    fun apply(
        meetings: List<Meeting>,
        filter: MeetingFilter,
        today: LocalDate,
        search: String = "",
    ): List<Meeting> {
        val needle = search.trim().lowercase()
        val filtered = meetings
            .filter { filter.matches(it, today) }
            .filter { needle.isEmpty() || it.matchesSearch(needle) }
        return when (filter) {
            // Soonest first when looking forward, most recent first when looking back.
            MeetingFilter.UPCOMING -> filtered.sortedWith(
                compareBy<Meeting> { it.day }.thenBy(nullsLast<LocalTime>()) { it.startTime },
            )

            MeetingFilter.PAST -> filtered.sortedWith(
                compareByDescending<Meeting> { it.day }
                    .thenByDescending(nullsFirst<LocalTime>()) { it.startTime },
            )

            MeetingFilter.NEEDS_FOLLOW_UP -> filtered.sortedWith(
                compareBy<Meeting> { it.nextTouch ?: LocalDate.MAX }.thenBy { it.day },
            )
        }
    }

    private fun Meeting.matchesSearch(lowercaseNeedle: String): Boolean =
        title.lowercase().contains(lowercaseNeedle) ||
            attendees.lowercase().contains(lowercaseNeedle) ||
            location.lowercase().contains(lowercaseNeedle) ||
            notes.lowercase().contains(lowercaseNeedle)
}

/** A day heading plus the entries written on it. */
data class LogDay(val day: LocalDate, val entries: List<LogEntry>)

object LogQuery {

    /** Newest day first, newest entry first inside a day. [kind] null means all kinds. */
    fun groupByDay(
        entries: List<LogEntry>,
        kind: LogKind? = null,
        search: String = "",
    ): List<LogDay> {
        val needle = search.trim().lowercase()
        return entries
            .filter { kind == null || it.kind == kind }
            .filter { needle.isEmpty() || it.body.lowercase().contains(needle) }
            .groupBy { it.day }
            .map { (day, dayEntries) ->
                LogDay(day, dayEntries.sortedByDescending { it.createdAt })
            }
            .sortedByDescending { it.day }
    }
}
