package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TodayBuilderTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 3)

    private fun task(
        id: Long,
        title: String = "Task $id",
        status: TaskStatus = TaskStatus.OPEN,
        due: LocalDate? = null,
        priority: Priority = Priority.MEDIUM,
        completedAt: Long? = null,
    ) = Task(
        id = id,
        title = title,
        status = status,
        dueDate = due,
        priority = priority,
        completedAt = completedAt,
        createdAt = id,
        updatedAt = id,
    )

    private fun millisOn(date: LocalDate): Long =
        date.atTime(11, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `overdue holds only open tasks with a past due date`() {
        val board = TodayBuilder.build(
            tasks = listOf(
                task(1, due = today.minusDays(2)),
                task(2, due = today),
                task(3, due = today.plusDays(1)),
                task(4, status = TaskStatus.DONE, due = today.minusDays(5)),
                task(5, status = TaskStatus.CANCELLED, due = today.minusDays(5)),
            ),
            meetings = emptyList(),
            logEntries = emptyList(),
            today = today,
        )

        assertEquals(listOf(1L), board.overdue.map { it.id })
        assertEquals(listOf(2L), board.dueToday.map { it.id })
    }

    @Test
    fun `a future dated task appears in no section`() {
        val board = TodayBuilder.build(
            tasks = listOf(task(1, due = today.plusDays(3))),
            meetings = emptyList(),
            logEntries = emptyList(),
            today = today,
        )

        assertTrue(board.overdue.isEmpty())
        assertTrue(board.dueToday.isEmpty())
        assertTrue(board.inProgress.isEmpty())
        assertTrue(board.isEmpty)
    }

    @Test
    fun `in progress catches started work that is not due yet`() {
        val board = TodayBuilder.build(
            tasks = listOf(
                task(1, status = TaskStatus.IN_PROGRESS),
                task(2, status = TaskStatus.IN_PROGRESS, due = today.plusDays(4)),
                // Due today wins over in-progress so nothing is listed twice.
                task(3, status = TaskStatus.IN_PROGRESS, due = today),
            ),
            meetings = emptyList(),
            logEntries = emptyList(),
            today = today,
        )

        assertEquals(setOf(1L, 2L), board.inProgress.map { it.id }.toSet())
        assertEquals(listOf(3L), board.dueToday.map { it.id })
    }

    @Test
    fun `no task can land in two sections`() {
        val tasks = listOf(
            task(1, due = today.minusDays(1), status = TaskStatus.IN_PROGRESS),
            task(2, due = today, status = TaskStatus.IN_PROGRESS),
            task(3, status = TaskStatus.IN_PROGRESS),
            task(4, due = today.minusDays(9)),
        )
        val board = TodayBuilder.build(tasks, emptyList(), emptyList(), today)

        val ids = board.overdue.map { it.id } + board.dueToday.map { it.id } +
            board.inProgress.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(setOf(1L, 2L, 3L, 4L), ids.toSet())
    }

    @Test
    fun `overdue sorts oldest first then loudest priority`() {
        val board = TodayBuilder.build(
            tasks = listOf(
                task(1, due = today.minusDays(1), priority = Priority.LOW),
                task(2, due = today.minusDays(7), priority = Priority.LOW),
                task(3, due = today.minusDays(1), priority = Priority.URGENT),
            ),
            meetings = emptyList(),
            logEntries = emptyList(),
            today = today,
        )

        assertEquals(listOf(2L, 3L, 1L), board.overdue.map { it.id })
    }

    @Test
    fun `due today sorts by priority descending`() {
        val board = TodayBuilder.build(
            tasks = listOf(
                task(1, due = today, priority = Priority.LOW),
                task(2, due = today, priority = Priority.URGENT),
                task(3, due = today, priority = Priority.HIGH),
            ),
            meetings = emptyList(),
            logEntries = emptyList(),
            today = today,
        )

        assertEquals(listOf(2L, 3L, 1L), board.dueToday.map { it.id })
    }

    @Test
    fun `completed today counts only tasks closed today`() {
        val board = TodayBuilder.build(
            tasks = listOf(
                task(1, status = TaskStatus.DONE, completedAt = millisOn(today)),
                task(2, status = TaskStatus.DONE, completedAt = millisOn(today)),
                task(3, status = TaskStatus.DONE, completedAt = millisOn(today.minusDays(1))),
                task(4, status = TaskStatus.DONE, completedAt = null),
            ),
            meetings = emptyList(),
            logEntries = emptyList(),
            today = today,
        )

        assertEquals(2, board.completedToday)
    }

    @Test
    fun `meetings are only todays and timed ones come first`() {
        val board = TodayBuilder.build(
            tasks = emptyList(),
            meetings = listOf(
                Meeting(id = 1, title = "Untimed", day = today, startTime = null),
                Meeting(id = 2, title = "Late", day = today, startTime = LocalTime.of(16, 0)),
                Meeting(id = 3, title = "Early", day = today, startTime = LocalTime.of(9, 30)),
                Meeting(id = 4, title = "Tomorrow", day = today.plusDays(1)),
            ),
            logEntries = emptyList(),
            today = today,
        )

        assertEquals(listOf(3L, 2L, 1L), board.meetings.map { it.id })
    }

    @Test
    fun `follow ups include overdue ones and exclude finished ones`() {
        val board = TodayBuilder.build(
            tasks = emptyList(),
            meetings = listOf(
                Meeting(id = 1, title = "Due today", day = today.minusDays(3), nextTouch = today),
                Meeting(id = 2, title = "Late", day = today.minusDays(9), nextTouch = today.minusDays(4)),
                Meeting(id = 3, title = "Later", day = today, nextTouch = today.plusDays(5)),
                Meeting(
                    id = 4,
                    title = "Handled",
                    day = today.minusDays(2),
                    nextTouch = today,
                    followUpDone = true,
                ),
                Meeting(id = 5, title = "No follow-up", day = today.minusDays(1)),
            ),
            logEntries = emptyList(),
            today = today,
        )

        assertEquals(listOf(2L, 1L), board.followUps.map { it.id })
    }

    @Test
    fun `log holds only todays entries newest first`() {
        val board = TodayBuilder.build(
            tasks = emptyList(),
            meetings = emptyList(),
            logEntries = listOf(
                LogEntry(id = 1, day = today, body = "first", createdAt = 100),
                LogEntry(id = 2, day = today, body = "second", createdAt = 500),
                LogEntry(id = 3, day = today.minusDays(1), body = "old", createdAt = 900),
            ),
            today = today,
        )

        assertEquals(listOf(2L, 1L), board.log.map { it.id })
    }

    @Test
    fun `open count adds overdue due today and follow ups`() {
        val board = TodayBuilder.build(
            tasks = listOf(
                task(1, due = today.minusDays(1)),
                task(2, due = today),
                task(3, status = TaskStatus.IN_PROGRESS),
            ),
            meetings = listOf(
                Meeting(id = 9, title = "Recap", day = today.minusDays(1), nextTouch = today),
            ),
            logEntries = listOf(LogEntry(id = 1, day = today, body = "note", kind = LogKind.NOTE)),
            today = today,
        )

        assertEquals(3, board.openCount)
        assertFalse(board.isEmpty)
    }

    @Test
    fun `an empty day is reported as empty`() {
        val board = TodayBuilder.build(emptyList(), emptyList(), emptyList(), today)

        assertTrue(board.isEmpty)
        assertEquals(0, board.openCount)
        assertEquals(today, board.day)
    }
}
