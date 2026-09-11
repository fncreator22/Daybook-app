package com.sr2ma.daybook.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** A unit of work. Action items captured in a meeting are tasks with [meetingId] set. */
data class Task(
    val id: Long = 0L,
    val title: String,
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.OPEN,
    val dueDate: LocalDate? = null,
    val project: String? = null,
    val meetingId: Long? = null,
    val cadence: Cadence = Cadence.NONE,
    val cadenceParentId: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val completedAt: Long? = null,
) {
    val isOpen: Boolean get() = !status.isClosed

    fun isOverdue(today: LocalDate): Boolean =
        isOpen && dueDate != null && dueDate.isBefore(today)

    fun isDueOn(day: LocalDate): Boolean = dueDate == day

    fun isDueBy(day: LocalDate): Boolean = dueDate != null && !dueDate.isAfter(day)
}

/** One line in the daily log. */
data class LogEntry(
    val id: Long = 0L,
    val day: LocalDate,
    val body: String,
    val kind: LogKind = LogKind.NOTE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * A meeting or call, plus the follow-up it created. [nextTouch] is the date the
 * next contact is owed; it stays outstanding until [followUpDone].
 */
data class Meeting(
    val id: Long = 0L,
    val title: String,
    val attendees: String = "",
    val day: LocalDate,
    val startTime: LocalTime? = null,
    val location: String = "",
    val notes: String = "",
    val nextTouch: LocalDate? = null,
    val followUpDone: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** Attendees are entered as free text; split on commas for display and counting. */
    val attendeeList: List<String>
        get() = attendees.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun needsFollowUpBy(day: LocalDate): Boolean =
        !followUpDone && nextTouch != null && !nextTouch.isAfter(day)
}
