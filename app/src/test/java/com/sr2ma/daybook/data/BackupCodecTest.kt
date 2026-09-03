package com.sr2ma.daybook.data

import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime

/**
 * The backup file is the user's only way to move their data, so the property that
 * matters most is that a round trip changes nothing at all.
 */
class BackupCodecTest {

    private val codec = BackupCodec()

    private val task = Task(
        id = 7L,
        title = "Draft the release notes",
        notes = "Mention the export flow",
        priority = Priority.HIGH,
        status = TaskStatus.IN_PROGRESS,
        dueDate = LocalDate.of(2026, 9, 4),
        project = "Launch",
        meetingId = 3L,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_600_000L,
        completedAt = null,
    )

    private val entry = LogEntry(
        id = 11L,
        day = LocalDate.of(2026, 9, 3),
        body = "Shipped the export flow",
        kind = LogKind.WIN,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    private val meeting = Meeting(
        id = 3L,
        title = "Weekly sync",
        attendees = "Ana, Bo",
        day = LocalDate.of(2026, 9, 3),
        startTime = LocalTime.of(10, 30),
        location = "Room 2",
        notes = "Agreed the cutover date",
        nextTouch = LocalDate.of(2026, 9, 10),
        followUpDone = false,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    private val snapshot = Snapshot(listOf(task), listOf(entry), listOf(meeting))

    @Test
    fun `round trip preserves every field`() {
        assertEquals(snapshot, codec.decode(codec.encode(snapshot)))
    }

    @Test
    fun `round trip through streams preserves every field`() {
        val out = ByteArrayOutputStream()
        codec.write(snapshot, out)
        val decoded = codec.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `an empty dataset round trips to an empty dataset`() {
        assertEquals(Snapshot(), codec.decode(codec.encode(Snapshot())))
    }

    @Test
    fun `optional empty fields are left out of the file`() {
        val bare = Task(id = 1L, title = "Just a title")
        val json = JSONObject(codec.encode(Snapshot(tasks = listOf(bare))))
            .getJSONArray("tasks")
            .getJSONObject(0)
        assertFalse(json.has("notes"))
        assertFalse(json.has("dueDate"))
        assertFalse(json.has("project"))
        assertFalse(json.has("meetingId"))
        assertFalse(json.has("completedAt"))
    }

    @Test
    fun `a task with no title is dropped rather than failing the import`() {
        val file = """
            {"format":"daybook-backup","version":1,
             "tasks":[{"id":1,"title":"  "},{"id":2,"title":"Real work"}]}
        """.trimIndent()
        val tasks = codec.decode(file).tasks
        assertEquals(1, tasks.size)
        assertEquals("Real work", tasks.first().title)
    }

    @Test
    fun `missing optional fields fall back to the model defaults`() {
        val file = """{"format":"daybook-backup","version":1,"tasks":[{"title":"Bare"}]}"""
        val decoded = codec.decode(file).tasks.single()
        assertEquals(0L, decoded.id)
        assertEquals("", decoded.notes)
        assertEquals(Priority.MEDIUM, decoded.priority)
        assertEquals(TaskStatus.OPEN, decoded.status)
        assertNull(decoded.dueDate)
        assertNull(decoded.project)
        assertNull(decoded.meetingId)
        assertNull(decoded.completedAt)
    }

    @Test
    fun `unknown fields and unknown enum values are tolerated`() {
        val file = """
            {"format":"daybook-backup","version":1,"somethingNew":true,
             "tasks":[{"title":"Odd","priority":"PANIC","status":"SNOOZED","futureField":1}]}
        """.trimIndent()
        val decoded = codec.decode(file).tasks.single()
        assertEquals(Priority.MEDIUM, decoded.priority)
        assertEquals(TaskStatus.OPEN, decoded.status)
    }

    @Test
    fun `an explicit JSON null reads as absent`() {
        val file = """
            {"format":"daybook-backup","version":1,
             "tasks":[{"title":"Nulls","notes":null,"dueDate":null,"meetingId":null}]}
        """.trimIndent()
        val decoded = codec.decode(file).tasks.single()
        assertEquals("", decoded.notes)
        assertNull(decoded.dueDate)
        assertNull(decoded.meetingId)
    }

    @Test
    fun `a log entry or meeting without a usable day is dropped`() {
        val file = """
            {"format":"daybook-backup","version":1,
             "logEntries":[{"body":"No day"},{"day":"not-a-date","body":"Bad day"},
                           {"day":"2026-09-03","body":"Good"}],
             "meetings":[{"title":"No day"},{"day":"2026-09-03","title":"Good"}]}
        """.trimIndent()
        val decoded = codec.decode(file)
        assertEquals(listOf("Good"), decoded.logEntries.map { it.body })
        assertEquals(listOf("Good"), decoded.meetings.map { it.title })
    }

    @Test
    fun `a file that is not a Daybook backup is rejected`() {
        val other = """{"items":[{"title":"From another app"}]}"""
        val failure = runCatching { codec.decode(other) }.exceptionOrNull()
        assertTrue(failure is BackupFormatException)
    }

    @Test
    fun `a file that is not JSON at all is rejected`() {
        val failure = runCatching { codec.decode("not json, just words") }.exceptionOrNull()
        assertTrue(failure is BackupFormatException)
    }

    @Test
    fun `a backup from a newer format version is rejected rather than half read`() {
        val file = """{"format":"daybook-backup","version":99,"tasks":[]}"""
        val failure = runCatching { codec.decode(file) }.exceptionOrNull()
        assertTrue(failure is BackupFormatException)
    }

    @Test
    fun `the file names itself and its version`() {
        val root = JSONObject(codec.encode(snapshot, exportedAt = 1_700_000_000_000L))
        assertEquals(BackupCodec.FORMAT, root.getString("format"))
        assertEquals(BackupCodec.VERSION, root.getInt("version"))
        assertEquals(1_700_000_000_000L, root.getLong("exportedAt"))
        assertEquals("2023-11-14T22:13:20Z", root.getString("exportedAtUtc"))
    }

    @Test
    fun `the suggested file name carries the day`() {
        assertEquals(
            "daybook-backup-2026-09-03.json",
            BackupCodec.suggestedFileName(LocalDate.of(2026, 9, 3)),
        )
    }
}
