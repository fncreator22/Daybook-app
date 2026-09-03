package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TaskQueryTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 3)

    private val overdue = Task(
        id = 1, title = "Send the recap", status = TaskStatus.OPEN,
        dueDate = today.minusDays(2), priority = Priority.HIGH, createdAt = 10,
    )
    private val dueToday = Task(
        id = 2, title = "Review the deck", status = TaskStatus.IN_PROGRESS,
        dueDate = today, priority = Priority.URGENT, project = "Onboarding", createdAt = 20,
    )
    private val future = Task(
        id = 3, title = "Plan the offsite", dueDate = today.plusDays(10),
        priority = Priority.LOW, project = "Team", createdAt = 30,
    )
    private val undated = Task(
        id = 4, title = "Read the spec", notes = "Section 4 covers export",
        priority = Priority.MEDIUM, createdAt = 40,
    )
    private val done = Task(
        id = 5, title = "Ship the export", status = TaskStatus.DONE,
        dueDate = today.minusDays(1), priority = Priority.URGENT,
        project = "Onboarding", createdAt = 50, completedAt = 60,
    )
    private val cancelled = Task(
        id = 6, title = "Chase the vendor", status = TaskStatus.CANCELLED, createdAt = 60,
    )

    private val all = listOf(overdue, dueToday, future, undated, done, cancelled)

    private fun ids(filter: TaskFilter, sort: TaskSort = TaskSort.PRIORITY): List<Long> =
        TaskQuery.apply(all, filter, sort, today).map { it.id }

    @Test
    fun `all keeps every task`() {
        assertEquals(all.size, ids(TaskFilter.ALL).size)
    }

    @Test
    fun `open excludes done and cancelled`() {
        assertEquals(setOf(1L, 2L, 3L, 4L), ids(TaskFilter.OPEN).toSet())
    }

    @Test
    fun `today means due today or already late`() {
        assertEquals(setOf(1L, 2L), ids(TaskFilter.TODAY).toSet())
    }

    @Test
    fun `overdue excludes closed work`() {
        assertEquals(listOf(1L), ids(TaskFilter.OVERDUE))
    }

    @Test
    fun `done shows only completed work, not cancelled`() {
        assertEquals(listOf(5L), ids(TaskFilter.DONE))
    }

    @Test
    fun `closed work sinks to the bottom in every sort`() {
        TaskSort.entries.forEach { sort ->
            val result = TaskQuery.apply(all, TaskFilter.ALL, sort, today)
            val firstClosed = result.indexOfFirst { it.status.isClosed }
            val lastOpen = result.indexOfLast { !it.status.isClosed }
            assertTrue("open work must precede closed work with $sort", lastOpen < firstClosed)
        }
    }

    @Test
    fun `priority sort puts the loudest open task first`() {
        val result = TaskQuery.apply(all, TaskFilter.OPEN, TaskSort.PRIORITY, today)
        assertEquals(2L, result.first().id)
    }

    @Test
    fun `due date sort puts the nearest date first and undated last`() {
        val result = TaskQuery.apply(all, TaskFilter.OPEN, TaskSort.DUE_DATE, today)
        assertEquals(listOf(1L, 2L, 3L, 4L), result.map { it.id })
    }

    @Test
    fun `newest sort is by creation time descending`() {
        val result = TaskQuery.apply(all, TaskFilter.OPEN, TaskSort.NEWEST, today)
        assertEquals(listOf(4L, 3L, 2L, 1L), result.map { it.id })
    }

    @Test
    fun `project narrows the list`() {
        val result = TaskQuery.apply(
            all, TaskFilter.ALL, TaskSort.PRIORITY, today, project = "Onboarding",
        )
        assertEquals(setOf(2L, 5L), result.map { it.id }.toSet())
    }

    @Test
    fun `search matches title notes and project, case insensitively`() {
        fun search(term: String) =
            TaskQuery.apply(all, TaskFilter.ALL, TaskSort.PRIORITY, today, search = term)
                .map { it.id }
                .toSet()

        assertEquals(setOf(1L), search("RECAP"))
        assertEquals(setOf(4L), search("section 4"))
        assertEquals(setOf(2L, 5L), search("onboard"))
        assertEquals(emptySet<Long>(), search("nothing here"))
        assertEquals(all.map { it.id }.toSet(), search("   "))
    }

    @Test
    fun `group by project keeps unfiled last and sorts case insensitively`() {
        val groups = TaskQuery.groupByProject(
            listOf(
                undated,
                dueToday,
                future,
                Task(id = 7, title = "alpha work", project = "alpha"),
            ),
        )

        assertEquals(listOf("alpha", "Onboarding", "Team", null), groups.map { it.project })
        assertEquals(listOf(4L), groups.last().tasks.map { it.id })
    }

    @Test
    fun `for meeting returns that meetings items, open first`() {
        val items = TaskQuery.forMeeting(
            listOf(
                Task(id = 1, title = "a", meetingId = 7, status = TaskStatus.DONE, createdAt = 1),
                Task(id = 2, title = "b", meetingId = 7, priority = Priority.LOW, createdAt = 2),
                Task(id = 3, title = "c", meetingId = 8, createdAt = 3),
                Task(id = 4, title = "d", meetingId = 7, priority = Priority.URGENT, createdAt = 4),
            ),
            meetingId = 7,
        )

        assertEquals(listOf(4L, 2L, 1L), items.map { it.id })
    }
}

class MeetingQueryTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 3)

    private val past = Meeting(
        id = 1, title = "Kickoff", attendees = "Ana, Ben", day = today.minusDays(4),
        startTime = LocalTime.of(10, 0), location = "Room 2",
    )
    private val yesterday = Meeting(
        id = 2, title = "Retro", day = today.minusDays(1), nextTouch = today, notes = "action items",
    )
    private val todayMeeting = Meeting(
        id = 3, title = "Standup", day = today, startTime = LocalTime.of(9, 15),
    )
    private val soon = Meeting(
        id = 4, title = "Vendor call", day = today.plusDays(2), startTime = LocalTime.of(14, 0),
    )
    private val untimedFuture = Meeting(id = 5, title = "Planning", day = today.plusDays(2))
    private val handled = Meeting(
        id = 6, title = "Closed loop", day = today.minusDays(6),
        nextTouch = today.minusDays(1), followUpDone = true,
    )

    private val all = listOf(past, yesterday, todayMeeting, soon, untimedFuture, handled)

    @Test
    fun `upcoming includes today and sorts soonest first with untimed last`() {
        val result = MeetingQuery.apply(all, MeetingFilter.UPCOMING, today)
        assertEquals(listOf(3L, 4L, 5L), result.map { it.id })
    }

    @Test
    fun `past excludes today and sorts most recent first`() {
        val result = MeetingQuery.apply(all, MeetingFilter.PAST, today)
        assertEquals(listOf(2L, 1L, 6L), result.map { it.id })
    }

    @Test
    fun `needs follow up ignores finished follow ups and those with no next touch`() {
        val result = MeetingQuery.apply(all, MeetingFilter.NEEDS_FOLLOW_UP, today)
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `search covers title attendees location and notes`() {
        fun search(term: String, filter: MeetingFilter = MeetingFilter.PAST) =
            MeetingQuery.apply(all, filter, today, search = term).map { it.id }.toSet()

        assertEquals(setOf(1L), search("ben"))
        assertEquals(setOf(1L), search("room 2"))
        assertEquals(setOf(2L), search("ACTION"))
        assertEquals(setOf(3L), search("stand", MeetingFilter.UPCOMING))
    }
}

class LogQueryTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 3)

    private val entries = listOf(
        LogEntry(id = 1, day = today, body = "Shipped export", kind = LogKind.WIN, createdAt = 100),
        LogEntry(id = 2, day = today, body = "Blocked on keys", kind = LogKind.BLOCKER, createdAt = 300),
        LogEntry(id = 3, day = today.minusDays(1), body = "Chose SQLite", kind = LogKind.DECISION, createdAt = 200),
        LogEntry(id = 4, day = today.minusDays(3), body = "Kickoff notes", kind = LogKind.NOTE, createdAt = 50),
    )

    @Test
    fun `days come newest first and entries within a day newest first`() {
        val days = LogQuery.groupByDay(entries)

        assertEquals(listOf(today, today.minusDays(1), today.minusDays(3)), days.map { it.day })
        assertEquals(listOf(2L, 1L), days.first().entries.map { it.id })
    }

    @Test
    fun `kind filters to a single kind`() {
        val days = LogQuery.groupByDay(entries, kind = LogKind.BLOCKER)

        assertEquals(1, days.size)
        assertEquals(listOf(2L), days.first().entries.map { it.id })
    }

    @Test
    fun `search is case insensitive and drops days with no match`() {
        val days = LogQuery.groupByDay(entries, search = "SQLITE")

        assertEquals(listOf(today.minusDays(1)), days.map { it.day })
    }

    @Test
    fun `an empty log produces no days`() {
        assertTrue(LogQuery.groupByDay(emptyList()).isEmpty())
    }
}
