package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.Cadence
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.SuggestionType
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AgentEngineTest {

    private val today = LocalDate.of(2026, 9, 11)

    private fun epochMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun openTask(
        id: Long,
        title: String = "Task $id",
        updatedDaysAgo: Long = 0,
        status: TaskStatus = TaskStatus.OPEN,
        cadence: Cadence = Cadence.NONE,
        dueDate: LocalDate? = null,
    ) = Task(
        id = id,
        title = title,
        status = status,
        cadence = cadence,
        dueDate = dueDate,
        createdAt = epochMillis(today.minusDays(updatedDaysAgo + 1)),
        updatedAt = epochMillis(today.minusDays(updatedDaysAgo)),
    )

    // ── STALE_TASK suggestions ────────────────────────────────────────────────

    @Test
    fun `stale open task produces STALE_TASK suggestion`() {
        val staleTask = openTask(id = 1, updatedDaysAgo = 8)
        val suggestions = AgentEngine.computeSuggestions(
            tasks = listOf(staleTask),
            meetings = emptyList(),
            today = today,
        )
        assertTrue(suggestions.any { it.type == SuggestionType.STALE_TASK && it.relatedEntityId == 1L })
    }

    @Test
    fun `fresh open task does not produce STALE_TASK suggestion`() {
        val freshTask = openTask(id = 1, updatedDaysAgo = 3)
        val suggestions = AgentEngine.computeSuggestions(
            tasks = listOf(freshTask),
            meetings = emptyList(),
            today = today,
        )
        assertTrue(suggestions.none { it.type == SuggestionType.STALE_TASK })
    }

    @Test
    fun `IN_PROGRESS task updated 30 days ago does not produce STALE_TASK`() {
        val task = openTask(id = 1, updatedDaysAgo = 30, status = TaskStatus.IN_PROGRESS)
        val suggestions = AgentEngine.computeSuggestions(listOf(task), emptyList(), today)
        assertTrue(suggestions.none { it.type == SuggestionType.STALE_TASK })
    }

    @Test
    fun `multiple stale tasks each get their own suggestion`() {
        val tasks = listOf(
            openTask(id = 1, updatedDaysAgo = 8),
            openTask(id = 2, updatedDaysAgo = 14),
        )
        val suggestions = AgentEngine.computeSuggestions(tasks, emptyList(), today)
        val staleSuggestions = suggestions.filter { it.type == SuggestionType.STALE_TASK }
        assertEquals(2, staleSuggestions.size)
    }

    // ── FOLLOW_UP_DUE suggestions ─────────────────────────────────────────────

    @Test
    fun `meeting with overdue follow-up produces FOLLOW_UP_DUE suggestion`() {
        val meeting = Meeting(
            id = 10L,
            title = "Client call",
            day = today.minusDays(3),
            nextTouch = today.minusDays(1),
            followUpDone = false,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val suggestions = AgentEngine.computeSuggestions(emptyList(), listOf(meeting), today)
        assertTrue(suggestions.any { it.type == SuggestionType.FOLLOW_UP_DUE && it.relatedEntityId == 10L })
    }

    @Test
    fun `meeting with follow-up done does not produce suggestion`() {
        val meeting = Meeting(
            id = 10L,
            title = "Done call",
            day = today.minusDays(3),
            nextTouch = today.minusDays(1),
            followUpDone = true,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val suggestions = AgentEngine.computeSuggestions(emptyList(), listOf(meeting), today)
        assertTrue(suggestions.none { it.type == SuggestionType.FOLLOW_UP_DUE })
    }

    @Test
    fun `meeting with future follow-up does not produce suggestion yet`() {
        val meeting = Meeting(
            id = 10L,
            title = "Future call",
            day = today,
            nextTouch = today.plusDays(3),
            followUpDone = false,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val suggestions = AgentEngine.computeSuggestions(emptyList(), listOf(meeting), today)
        assertTrue(suggestions.none { it.type == SuggestionType.FOLLOW_UP_DUE })
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    fun `no tasks no meetings produces empty suggestion list`() {
        val suggestions = AgentEngine.computeSuggestions(emptyList(), emptyList(), today)
        assertTrue(suggestions.isEmpty())
    }
}
