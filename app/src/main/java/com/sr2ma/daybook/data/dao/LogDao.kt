package com.sr2ma.daybook.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.mapRows
import com.sr2ma.daybook.data.reqLong
import com.sr2ma.daybook.data.reqString
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import java.time.LocalDate

/** All reads and writes for the `log_entries` table. */
class LogDao(private val helper: DaybookDatabase) {

    fun all(): List<LogEntry> =
        helper.readableDatabase.rawQuery(SELECT_ALL, null).mapRows(::readEntry)

    /** See [com.sr2ma.daybook.data.dao.TaskDao.upsert] for why this is not `insert`. */
    fun upsert(entry: LogEntry): Long {
        val db = helper.writableDatabase
        val values = entry.toContentValues()
        if (entry.id == 0L) {
            return db.insertOrThrow(DaybookDatabase.TABLE_LOG_ENTRIES, null, values)
        }
        val updated = db.update(
            DaybookDatabase.TABLE_LOG_ENTRIES,
            values,
            WHERE_ID,
            arrayOf(entry.id.toString()),
        )
        return if (updated > 0) {
            entry.id
        } else {
            db.insertOrThrow(DaybookDatabase.TABLE_LOG_ENTRIES, null, values)
        }
    }

    fun delete(id: Long): Int =
        helper.writableDatabase.delete(
            DaybookDatabase.TABLE_LOG_ENTRIES,
            WHERE_ID,
            arrayOf(id.toString()),
        )

    fun deleteAll(): Int =
        helper.writableDatabase.delete(DaybookDatabase.TABLE_LOG_ENTRIES, null, null)

    private fun LogEntry.toContentValues(): ContentValues = ContentValues().apply {
        put("day", Dates.store(day))
        put("body", body)
        put("kind", kind.storedValue)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun readEntry(cursor: Cursor): LogEntry = LogEntry(
        id = cursor.reqLong("id"),
        // A row can only exist with a valid day, but fall back rather than crash
        // if a hand-edited backup ever slips a bad value through.
        day = Dates.parseDate(cursor.reqString("day")) ?: LocalDate.EPOCH,
        body = cursor.reqString("body"),
        kind = LogKind.fromStored(cursor.reqString("kind")),
        createdAt = cursor.reqLong("created_at"),
        updatedAt = cursor.reqLong("updated_at"),
    )

    private companion object {
        const val WHERE_ID = "id = ?"

        // Newest day first, and within a day the most recent entry first.
        const val SELECT_ALL = "SELECT * FROM log_entries ORDER BY day DESC, created_at DESC"
    }
}
