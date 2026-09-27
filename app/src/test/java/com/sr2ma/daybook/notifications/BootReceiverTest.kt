package com.sr2ma.daybook.notifications

import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BootReceiverTest {

    private val today = LocalDate.of(2026, 9, 26)

    @Test
    fun `isBootAction recognises standard and vendor boot completed broadcasts`() {
        assertTrue(BootReceiver.isBootAction(android.content.Intent.ACTION_BOOT_COMPLETED))
        assertTrue(BootReceiver.isBootAction(BootReceiver.ACTION_QUICKBOOT_POWERON))
        assertTrue(BootReceiver.isBootAction(BootReceiver.ACTION_HTC_QUICKBOOT))
        assertFalse(BootReceiver.isBootAction("android.intent.action.TIME_TICK"))
        assertFalse(BootReceiver.isBootAction(null))
    }

    @Test
    fun `open task with due date today or in future qualifies for rescheduling`() {
        val taskDueToday = Task(id = 1L, title = "Due today", status = TaskStatus.OPEN, dueDate = today)
        val taskDueFuture = Task(id = 2L, title = "Due future", status = TaskStatus.OPEN, dueDate = today.plusDays(3))
        val taskDuePast = Task(id = 3L, title = "Due past", status = TaskStatus.OPEN, dueDate = today.minusDays(1))
        val taskDone = Task(id = 4L, title = "Done task", status = TaskStatus.DONE, dueDate = today.plusDays(1))
        val taskNoDate = Task(id = 5L, title = "No date", status = TaskStatus.OPEN, dueDate = null)

        val allTasks = listOf(taskDueToday, taskDueFuture, taskDuePast, taskDone, taskNoDate)
        val eligible = BootReceiver.filterReschedulableTasks(allTasks, today)

        assertEquals(listOf(1L, 2L), eligible.map { it.id })
    }

    @Test
    fun `meeting today or in future qualifies for rescheduling`() {
        val meetingToday = Meeting(id = 10L, title = "Morning sync", day = today)
        val meetingFuture = Meeting(id = 11L, title = "Next week sync", day = today.plusDays(7))
        val meetingPast = Meeting(id = 12L, title = "Yesterday retrospective", day = today.minusDays(1))

        val allMeetings = listOf(meetingToday, meetingFuture, meetingPast)
        val eligible = BootReceiver.filterReschedulableMeetings(allMeetings, today)

        assertEquals(listOf(10L, 11L), eligible.map { it.id })
    }
}
