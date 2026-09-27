package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.Cadence
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Handles the lifecycle of recurring tasks.
 *
 * Two responsibilities:
 * 1. [onTaskCompleted] — produce the next Task instance when a cadenced task is marked DONE.
 * 2. [isStale] — determine whether an OPEN task has gone untouched too long (ADR-0001).
 *
 * Pure: no I/O, no Android deps, no coroutines. Inputs in, output out.
 */
object CadenceEngine {

    private const val STALE_DAYS = 7L

    /**
     * Called when [task] is marked DONE. Returns the next Task instance if the task
     * has a cadence, or null if [task.cadence] is NONE.
     *
     * The next due date is anchored to the original due date, not the completion date
     * (ADR-0003). If the task has no due date, the next instance also has no due date.
     *
     * The returned task has id = 0 (ready for DB insertion).
     */
    fun onTaskCompleted(task: Task, completedAt: LocalDate): Task? {
        if (task.cadence == Cadence.NONE) return null

        val nextDue = task.dueDate?.let { original ->
            when (task.cadence) {
                Cadence.DAILY -> original.plusDays(1)
                Cadence.WEEKLY -> original.plusDays(7)
                Cadence.MONTHLY -> original.plusMonths(1)
                Cadence.YEARLY -> original.plusYears(1)
                Cadence.NONE -> null
            }
        }

        val now = completedAt.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return task.copy(
            id = 0L,
            status = TaskStatus.OPEN,
            dueDate = nextDue,
            completedAt = null,
            cadenceParentId = task.id,
            createdAt = now,
            updatedAt = now,
            gcalEventId = null,
            syncStatus = if (task.calendarSyncEnabled) com.sr2ma.daybook.domain.model.SyncStatus.PENDING_SYNC else com.sr2ma.daybook.domain.model.SyncStatus.LOCAL_ONLY,
        )
    }

    /**
     * Returns true when [task] should surface a STALE_TASK AgentSuggestion.
     *
     * Rules (ADR-0001):
     * - Status must be OPEN (DONE, CANCELLED are terminal; IN_PROGRESS is suppressed)
     * - updated_at must be more than [STALE_DAYS] days before [today]
     */
    fun isStale(task: Task, today: LocalDate): Boolean {
        if (task.status != TaskStatus.OPEN) return false
        val updatedDay = epochMillisToLocalDate(task.updatedAt)
        return updatedDay.isBefore(today.minusDays(STALE_DAYS))
    }

    private fun epochMillisToLocalDate(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
}
