package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BriefingWriterTest {

    private val today = LocalDate.of(2026, 9, 11)
    private val zone = ZoneId.of("UTC")

    private fun task(
        id: Long,
        title: String = "Task $id",
        status: TaskStatus = TaskStatus.OPEN,
        due: LocalDate? = null,
        priority: Priority = Priority.MEDIUM,
    ) = Task(id = id, title = title, status = status, dueDate = due,
        priority = priority, createdAt = id, updatedAt = id)

    private fun board(
        overdue: List<Task> = emptyList(),
        dueToday: List<Task> = emptyList(),
        inProgress: List<Task> = emptyList(),
        meetings: List<Meeting> = emptyList(),
        followUps: List<Meeting> = emptyList(),
        log: List<LogEntry> = emptyList(),
        completedToday: Int = 0,
    ) = TodayBoard(
        day = today,
        overdue = overdue,
        dueToday = dueToday,
        inProgress = inProgress,
        meetings = meetings,
        followUps = followUps,
        log = log,
        completedToday = completedToday,
    )

    @Test
    fun `briefing for an empty board is a short positive message`() {
        val text = BriefingWriter.write(board())
        assertTrue(text.isNotBlank())
        // Should not claim tasks exist when there are none
        assertFalse(text.contains("overdue", ignoreCase = true))
    }

    @Test
    fun `overdue count appears in briefing when present`() {
        val b = board(overdue = listOf(task(1, due = today.minusDays(2))))
        val text = BriefingWriter.write(b)
        assertTrue(text.contains("overdue", ignoreCase = true))
    }

    @Test
    fun `single overdue task title appears in briefing`() {
        val b = board(overdue = listOf(task(1, title = "Fix the login bug", due = today.minusDays(1))))
        val text = BriefingWriter.write(b)
        assertTrue(text.contains("Fix the login bug"))
    }

    @Test
    fun `due today section appears when tasks are due`() {
        val b = board(dueToday = listOf(task(1, title = "Send invoice", due = today)))
        val text = BriefingWriter.write(b)
        assertTrue(text.contains("Send invoice"))
    }

    @Test
    fun `meeting is mentioned in briefing`() {
        val meeting = Meeting(id = 1, title = "Design review", day = today,
            attendees = "Alice, Bob", createdAt = 0L, updatedAt = 0L)
        val b = board(meetings = listOf(meeting))
        val text = BriefingWriter.write(b)
        assertTrue(text.contains("Design review", ignoreCase = true))
    }

    @Test
    fun `completed count is mentioned when positive`() {
        val b = board(completedToday = 3)
        val text = BriefingWriter.write(b)
        assertTrue(text.contains("3"))
    }

    @Test
    fun `briefing is a single non-empty string`() {
        val b = board(
            overdue = listOf(task(1, due = today.minusDays(3))),
            dueToday = listOf(task(2, due = today)),
            meetings = listOf(Meeting(id = 1, title = "Standup", day = today,
                createdAt = 0L, updatedAt = 0L)),
            completedToday = 1,
        )
        val text = BriefingWriter.write(b)
        assertTrue(text.isNotBlank())
        assertTrue(text.length > 20)
    }

    @Test
    fun `blockers from log appear in briefing`() {
        val b = board(
            log = listOf(LogEntry(id = 1, day = today, body = "waiting on legal",
                kind = LogKind.BLOCKER, createdAt = 0L, updatedAt = 0L))
        )
        val text = BriefingWriter.write(b)
        assertTrue(text.contains("waiting on legal", ignoreCase = true))
    }
}
