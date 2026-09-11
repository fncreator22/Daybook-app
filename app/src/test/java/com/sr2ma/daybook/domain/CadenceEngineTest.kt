package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.Cadence
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CadenceEngineTest {

    private val today = LocalDate.of(2026, 9, 11)

    private fun weeklyTask(
        id: Long = 1L,
        dueDate: LocalDate = today,
        notes: String = "",
        priority: Priority = Priority.MEDIUM,
        project: String? = null,
    ) = Task(
        id = id,
        title = "Weekly review",
        notes = notes,
        priority = priority,
        project = project,
        status = TaskStatus.OPEN,
        dueDate = dueDate,
        cadence = Cadence.WEEKLY,
        createdAt = 1L,
        updatedAt = 1L,
    )

    // ── Next instance creation ────────────────────────────────────────────────

    @Test
    fun `completing a weekly task produces a new OPEN instance due 7 days from original`() {
        val completed = weeklyTask(dueDate = today)
        val next = CadenceEngine.onTaskCompleted(completed, completedAt = today)

        assertEquals(TaskStatus.OPEN, next.status)
        assertEquals(today.plusDays(7), next.dueDate)
        assertNull(next.completedAt)
    }

    @Test
    fun `completing a daily task produces a next instance due 1 day from original`() {
        val task = weeklyTask(dueDate = today).copy(cadence = Cadence.DAILY)
        val next = CadenceEngine.onTaskCompleted(task, completedAt = today)

        assertEquals(today.plusDays(1), next.dueDate)
    }

    @Test
    fun `completing a monthly task produces next instance due 1 month from original`() {
        val task = weeklyTask(dueDate = today).copy(cadence = Cadence.MONTHLY)
        val next = CadenceEngine.onTaskCompleted(task, completedAt = today)

        assertEquals(today.plusMonths(1), next.dueDate)
    }

    @Test
    fun `completing a yearly task produces next instance due 1 year from original`() {
        val task = weeklyTask(dueDate = today).copy(cadence = Cadence.YEARLY)
        val next = CadenceEngine.onTaskCompleted(task, completedAt = today)

        assertEquals(today.plusYears(1), next.dueDate)
    }

    @Test
    fun `early completion anchors next due date to original not completion date`() {
        // Weekly task due Friday (Sep 11), completed on Tuesday (Sep 7)
        val friday = LocalDate.of(2026, 9, 11)
        val tuesday = LocalDate.of(2026, 9, 7)
        val task = weeklyTask(dueDate = friday)

        val next = CadenceEngine.onTaskCompleted(task, completedAt = tuesday)

        // Next due = original Friday + 7 = Sep 18, not Sep 14 (Tuesday + 7)
        assertEquals(LocalDate.of(2026, 9, 18), next.dueDate)
    }

    // ── Field propagation ─────────────────────────────────────────────────────

    @Test
    fun `title carries forward`() {
        val task = weeklyTask()
        val next = CadenceEngine.onTaskCompleted(task, today)
        assertEquals("Weekly review", next.title)
    }

    @Test
    fun `notes carry forward`() {
        val task = weeklyTask(notes = "Always bring the Q-report")
        val next = CadenceEngine.onTaskCompleted(task, today)
        assertEquals("Always bring the Q-report", next.notes)
    }

    @Test
    fun `priority carries forward`() {
        val task = weeklyTask(priority = Priority.HIGH)
        val next = CadenceEngine.onTaskCompleted(task, today)
        assertEquals(Priority.HIGH, next.priority)
    }

    @Test
    fun `project carries forward`() {
        val task = weeklyTask(project = "Acme")
        val next = CadenceEngine.onTaskCompleted(task, today)
        assertEquals("Acme", next.project)
    }

    @Test
    fun `cadence carries forward`() {
        val task = weeklyTask()
        val next = CadenceEngine.onTaskCompleted(task, today)
        assertEquals(Cadence.WEEKLY, next.cadence)
    }

    @Test
    fun `cadence parent id is set to the completed task id`() {
        val task = weeklyTask(id = 42L)
        val next = CadenceEngine.onTaskCompleted(task, today)
        assertEquals(42L, next.cadenceParentId)
    }

    @Test
    fun `new instance gets id of 0 for db insertion`() {
        val task = weeklyTask(id = 99L)
        val next = CadenceEngine.onTaskCompleted(task, today)
        assertEquals(0L, next.id)
    }

    // ── Non-cadenced tasks ────────────────────────────────────────────────────

    @Test
    fun `non-cadenced task returns null — no next instance created`() {
        val task = weeklyTask().copy(cadence = Cadence.NONE)
        val next = CadenceEngine.onTaskCompleted(task, today)
        assertNull(next)
    }

    // ── No due date ───────────────────────────────────────────────────────────

    @Test
    fun `cadenced task with no due date produces next instance with null due date`() {
        val task = weeklyTask(dueDate = today).copy(dueDate = null)
        val next = CadenceEngine.onTaskCompleted(task, today)
        assertNull(next?.dueDate)
    }

    // ── Staleness ─────────────────────────────────────────────────────────────

    @Test
    fun `task updated 8 days ago with OPEN status is stale`() {
        val task = Task(
            id = 1L, title = "Old task",
            status = TaskStatus.OPEN,
            updatedAt = today.minusDays(8).toEpochMilli(),
            createdAt = 0L,
        )
        val result = CadenceEngine.isStale(task, today)
        assertEquals(true, result)
    }

    @Test
    fun `task updated 6 days ago is not stale`() {
        val task = Task(
            id = 1L, title = "Recent task",
            status = TaskStatus.OPEN,
            updatedAt = today.minusDays(6).toEpochMilli(),
            createdAt = 0L,
        )
        val result = CadenceEngine.isStale(task, today)
        assertEquals(false, result)
    }

    @Test
    fun `IN_PROGRESS task updated 30 days ago is not stale`() {
        val task = Task(
            id = 1L, title = "Long running",
            status = TaskStatus.IN_PROGRESS,
            updatedAt = today.minusDays(30).toEpochMilli(),
            createdAt = 0L,
        )
        val result = CadenceEngine.isStale(task, today)
        assertEquals(false, result)
    }

    @Test
    fun `DONE task is never stale`() {
        val task = Task(
            id = 1L, title = "Done",
            status = TaskStatus.DONE,
            updatedAt = today.minusDays(100).toEpochMilli(),
            createdAt = 0L,
        )
        val result = CadenceEngine.isStale(task, today)
        assertEquals(false, result)
    }
}

// Extension helper — converts LocalDate to epoch millis at midnight UTC for tests
private fun LocalDate.toEpochMilli(): Long =
    atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
