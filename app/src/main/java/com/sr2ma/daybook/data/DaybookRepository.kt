package com.sr2ma.daybook.data

import com.sr2ma.daybook.data.dao.LogDao
import com.sr2ma.daybook.data.dao.MeetingDao
import com.sr2ma.daybook.data.dao.PassDao
import com.sr2ma.daybook.data.dao.TaskDao
import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The single door to stored data.
 *
 * Reads are published as [StateFlow]s that the UI collects, and every write
 * refreshes the affected flow. The dataset for a personal work tracker is small
 * enough that re-reading a table after a write is cheaper and far easier to
 * reason about than incremental cache updates.
 *
 * All disk work runs on [io], a single-threaded view of [Dispatchers.IO]. One
 * thread rather than the default sixty-four is the point: every write is followed
 * by a re-read that publishes into a flow, and on a shared pool two overlapping
 * saves can interleave so the flow ends up holding the *earlier* read. Serialising
 * makes each write-then-refresh atomic with respect to the others, and a personal
 * tracker has no throughput to lose by it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DaybookRepository(internal val database: DaybookDatabase) {

    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val taskDao = TaskDao(database)
    private val logDao = LogDao(database)
    private val meetingDao = MeetingDao(database)
    private val passDao = PassDao(database)
    private val whatsAppDao = com.sr2ma.daybook.data.dao.WhatsAppDao(database)
    private val gmailDao = com.sr2ma.daybook.data.dao.GmailDao(database)
    private val conversationDao = com.sr2ma.daybook.data.dao.ConversationDao(database)

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

    private val _meetings = MutableStateFlow<List<Meeting>>(emptyList())
    val meetings: StateFlow<List<Meeting>> = _meetings.asStateFlow()

    private val _projects = MutableStateFlow<List<String>>(emptyList())
    val projects: StateFlow<List<String>> = _projects.asStateFlow()

    private val _passes = MutableStateFlow<List<Pass>>(emptyList())
    val passes: StateFlow<List<Pass>> = _passes.asStateFlow()

    suspend fun refreshAll() = withContext(io) {
        _tasks.value = taskDao.all()
        _logEntries.value = logDao.all()
        _meetings.value = meetingDao.all()
        _projects.value = taskDao.projects()
        _passes.value = passDao.all()
    }

    /**
     * Runs [body] inside one SQLite transaction, so a multi-row write either lands
     * whole or not at all.
     *
     * This exists for the two operations that touch all three tables — wiping and
     * importing. Without it, a failure partway through an import leaves a
     * half-imported database, and in replace mode the old rows are already gone by
     * that point, which is the one way this app could actually lose someone's data.
     */
    private fun <T> inTransaction(body: () -> T): T {
        val db = database.writableDatabase
        db.beginTransaction()
        return try {
            val result = body()
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    // ---- Tasks ----------------------------------------------------------

    /** Inserts or updates [task], stamping timestamps. Returns the row id. */
    suspend fun saveTask(task: Task, now: Long = System.currentTimeMillis()): Long =
        withContext(io) {
            val stamped = task.copy(
                title = task.title.trim(),
                notes = task.notes.trim(),
                project = task.project?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = if (task.createdAt == 0L) now else task.createdAt,
                updatedAt = now,
                // Keep completedAt honest whichever way the status moved. Cancelled
                // is deliberately left alone: cancelling something already finished
                // should not erase the fact that it was finished, and cancelling
                // open work has nothing to erase.
                completedAt = when {
                    task.status == TaskStatus.DONE -> task.completedAt ?: now
                    task.status == TaskStatus.CANCELLED -> task.completedAt
                    else -> null
                },
            )
            val id = taskDao.upsert(stamped)
            refreshTasks()
            id
        }

    /** Flips a task between open and done in one step, for list checkboxes. */
    suspend fun toggleTaskDone(task: Task, now: Long = System.currentTimeMillis()) {
        val next = if (task.status == TaskStatus.DONE) TaskStatus.OPEN else TaskStatus.DONE
        saveTask(task.copy(status = next, completedAt = null), now)
    }

    suspend fun deleteTask(id: Long) = withContext(io) {
        taskDao.delete(id)
        refreshTasks()
    }

    private fun refreshTasks() {
        _tasks.value = taskDao.all()
        _projects.value = taskDao.projects()
    }

    // ---- Log ------------------------------------------------------------

    suspend fun saveLogEntry(entry: LogEntry, now: Long = System.currentTimeMillis()): Long =
        withContext(io) {
            val stamped = entry.copy(
                body = entry.body.trim(),
                createdAt = if (entry.createdAt == 0L) now else entry.createdAt,
                updatedAt = now,
            )
            val id = logDao.upsert(stamped)
            _logEntries.value = logDao.all()
            id
        }

    suspend fun deleteLogEntry(id: Long) = withContext(io) {
        logDao.delete(id)
        _logEntries.value = logDao.all()
    }

    // ---- Meetings -------------------------------------------------------

    suspend fun saveMeeting(meeting: Meeting, now: Long = System.currentTimeMillis()): Long =
        withContext(io) {
            val stamped = meeting.copy(
                title = meeting.title.trim(),
                attendees = meeting.attendees.trim(),
                location = meeting.location.trim(),
                notes = meeting.notes.trim(),
                createdAt = if (meeting.createdAt == 0L) now else meeting.createdAt,
                updatedAt = now,
            )
            val id = meetingDao.upsert(stamped)
            _meetings.value = meetingDao.all()
            id
        }

    /** Deleting a meeting leaves its action items as standalone tasks. */
    suspend fun deleteMeeting(id: Long) = withContext(io) {
        meetingDao.delete(id)
        _meetings.value = meetingDao.all()
        refreshTasks()
    }

    // ---- Whole-database operations --------------------------------------

    suspend fun deleteEverything() = withContext(io) {
        inTransaction {
            // Tasks first: they hold the foreign key into meetings.
            taskDao.deleteAll()
            logDao.deleteAll()
            meetingDao.deleteAll()
            passDao.deleteAll()
            whatsAppDao.deleteAll()
            gmailDao.deleteAll()
            conversationDao.deleteAll()
        }
        refreshAll()
    }

    /**
     * Writes a whole snapshot in. Ids are dropped so rows are appended rather
     * than overwriting whatever happens to share an id, and meeting links are
     * remapped onto the newly inserted meeting rows.
     *
     * The returned counts are the rows that actually landed, not the rows the file
     * offered, so the message the user sees cannot overstate what was imported.
     */
    suspend fun importSnapshot(
        snapshot: Snapshot,
        replaceExisting: Boolean,
        now: Long = System.currentTimeMillis(),
    ): DataCounts = withContext(io) {
        val counts = inTransaction {
            if (replaceExisting) {
                taskDao.deleteAll()
                logDao.deleteAll()
                meetingDao.deleteAll()
                passDao.deleteAll()
                whatsAppDao.deleteAll()
                gmailDao.deleteAll()
                conversationDao.deleteAll()
            }

            val remappedMeetingIds = HashMap<Long, Long>(snapshot.meetings.size)
            snapshot.meetings.forEach { meeting ->
                val newId = meetingDao.upsert(
                    meeting.copy(
                        id = 0L,
                        createdAt = if (meeting.createdAt == 0L) now else meeting.createdAt,
                        updatedAt = if (meeting.updatedAt == 0L) now else meeting.updatedAt,
                    ),
                )
                remappedMeetingIds[meeting.id] = newId
            }

            snapshot.tasks.forEach { task ->
                taskDao.upsert(
                    task.copy(
                        id = 0L,
                        meetingId = task.meetingId?.let { remappedMeetingIds[it] },
                        createdAt = if (task.createdAt == 0L) now else task.createdAt,
                        updatedAt = if (task.updatedAt == 0L) now else task.updatedAt,
                    ),
                )
            }

            snapshot.logEntries.forEach { entry ->
                logDao.upsert(
                    entry.copy(
                        id = 0L,
                        createdAt = if (entry.createdAt == 0L) now else entry.createdAt,
                        updatedAt = if (entry.updatedAt == 0L) now else entry.updatedAt,
                    ),
                )
            }

            DataCounts(snapshot.tasks.size, snapshot.logEntries.size, snapshot.meetings.size)
        }

        refreshAll()
        counts
    }

    /**
     * Everything currently stored, read from the database rather than from the
     * cached flows.
     *
     * A backup is the one thing that has to be right, and the flows hold whatever
     * the last refresh left there. Going to disk costs three queries and removes
     * the whole class of "the export was missing the row I just added".
     */
    suspend fun snapshot(): Snapshot = withContext(io) {
        Snapshot(
            tasks = taskDao.all(),
            logEntries = logDao.all(),
            meetings = meetingDao.all(),
        )
    }

    // ---- Passes ----------------------------------------------------------------

    suspend fun savePass(pass: Pass, now: Long = System.currentTimeMillis()): Long =
        withContext(io) {
            val stamped = pass.copy(
                title = pass.title.trim(),
                notes = pass.notes.trim(),
                createdAt = if (pass.createdAt == 0L) now else pass.createdAt,
                updatedAt = now,
            )
            val id = passDao.upsert(stamped)
            _passes.value = passDao.all()
            id
        }

    suspend fun deletePass(pass: Pass) = withContext(io) {
        passDao.delete(pass.id)
        _passes.value = passDao.all()
    }
}

/** Row counts, for the Settings screen. */
data class DataCounts(val tasks: Int, val logEntries: Int, val meetings: Int)

/** Everything Daybook stores, in one value. Used for export and import. */
data class Snapshot(
    val tasks: List<Task> = emptyList(),
    val logEntries: List<LogEntry> = emptyList(),
    val meetings: List<Meeting> = emptyList(),
)
