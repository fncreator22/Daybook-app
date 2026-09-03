package com.sr2ma.daybook.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.mapRows
import com.sr2ma.daybook.data.optLong
import com.sr2ma.daybook.data.optString
import com.sr2ma.daybook.data.reqInt
import com.sr2ma.daybook.data.reqLong
import com.sr2ma.daybook.data.reqString
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus

/** All reads and writes for the `tasks` table. */
class TaskDao(private val helper: DaybookDatabase) {

    fun all(): List<Task> =
        helper.readableDatabase.rawQuery(SELECT_ALL, null).mapRows(::readTask)

    /**
     * Inserts when [task] has id 0, otherwise updates. Returns the row id.
     *
     * `insertOrThrow` rather than `insert`, because `insert` reports a rejected row
     * as -1 and an import would then hand that -1 out as a real meeting id. An
     * update that matches nothing — the row was deleted from another screen while
     * this editor was open — falls through to an insert rather than silently
     * reporting success for a write that never happened.
     */
    fun upsert(task: Task): Long {
        val db = helper.writableDatabase
        val values = task.toContentValues()
        if (task.id == 0L) {
            return db.insertOrThrow(DaybookDatabase.TABLE_TASKS, null, values)
        }
        val updated = db.update(
            DaybookDatabase.TABLE_TASKS,
            values,
            WHERE_ID,
            arrayOf(task.id.toString()),
        )
        return if (updated > 0) {
            task.id
        } else {
            db.insertOrThrow(DaybookDatabase.TABLE_TASKS, null, values)
        }
    }

    fun delete(id: Long): Int =
        helper.writableDatabase.delete(
            DaybookDatabase.TABLE_TASKS,
            WHERE_ID,
            arrayOf(id.toString()),
        )

    fun deleteAll(): Int =
        helper.writableDatabase.delete(DaybookDatabase.TABLE_TASKS, null, null)

    /** Distinct project names, for the project suggestion chips. */
    fun projects(): List<String> =
        helper.readableDatabase.rawQuery(SELECT_PROJECTS, null).mapRows { it.reqString("project") }

    private fun Task.toContentValues(): ContentValues = ContentValues().apply {
        put("title", title)
        put("notes", notes)
        put("priority", priority.storedValue)
        put("status", status.storedValue)
        put("due_date", Dates.store(dueDate))
        put("project", project?.takeIf { it.isNotBlank() })
        put("meeting_id", meetingId)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("completed_at", completedAt)
    }

    private fun readTask(cursor: Cursor): Task = Task(
        id = cursor.reqLong("id"),
        title = cursor.reqString("title"),
        notes = cursor.reqString("notes"),
        priority = Priority.fromStored(cursor.reqInt("priority")),
        status = TaskStatus.fromStored(cursor.reqString("status")),
        dueDate = Dates.parseDate(cursor.optString("due_date")),
        project = cursor.optString("project"),
        meetingId = cursor.optLong("meeting_id"),
        createdAt = cursor.reqLong("created_at"),
        updatedAt = cursor.reqLong("updated_at"),
        completedAt = cursor.optLong("completed_at"),
    )

    private companion object {
        const val WHERE_ID = "id = ?"

        /**
         * One ordering for the whole app: open work before closed, then the
         * nearest due date, then the loudest priority. Screens re-slice this in
         * memory rather than issuing a query per filter.
         */
        const val SELECT_ALL = """
            SELECT * FROM tasks
            ORDER BY
                CASE WHEN status IN ('DONE', 'CANCELLED') THEN 1 ELSE 0 END ASC,
                CASE WHEN due_date IS NULL THEN 1 ELSE 0 END ASC,
                due_date ASC,
                priority DESC,
                created_at DESC
        """

        const val SELECT_PROJECTS = """
            SELECT DISTINCT project FROM tasks
            WHERE project IS NOT NULL AND TRIM(project) <> ''
            ORDER BY project COLLATE NOCASE ASC
        """
    }
}
