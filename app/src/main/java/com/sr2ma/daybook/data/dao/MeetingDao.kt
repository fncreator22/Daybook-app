package com.sr2ma.daybook.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.mapRows
import com.sr2ma.daybook.data.optString
import com.sr2ma.daybook.data.reqBoolean
import com.sr2ma.daybook.data.reqLong
import com.sr2ma.daybook.data.reqString
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.SyncStatus
import java.time.LocalDate

/** All reads and writes for the `meetings` table. */
class MeetingDao(private val helper: DaybookDatabase) {

    fun all(): List<Meeting> =
        helper.readableDatabase.rawQuery(SELECT_ALL, null).mapRows(::readMeeting)

    /** Returns all meetings whose sync_status = PENDING_SYNC. */
    fun pendingSync(): List<Meeting> =
        helper.readableDatabase.rawQuery(SELECT_PENDING_SYNC, null).mapRows(::readMeeting)

    /** See [com.sr2ma.daybook.data.dao.TaskDao.upsert] for why this is not `insert`. */
    fun upsert(meeting: Meeting): Long {
        val db = helper.writableDatabase
        val values = meeting.toContentValues()
        if (meeting.id == 0L) {
            return db.insertOrThrow(DaybookDatabase.TABLE_MEETINGS, null, values)
        }
        val updated = db.update(
            DaybookDatabase.TABLE_MEETINGS,
            values,
            WHERE_ID,
            arrayOf(meeting.id.toString()),
        )
        return if (updated > 0) {
            meeting.id
        } else {
            db.insertOrThrow(DaybookDatabase.TABLE_MEETINGS, null, values)
        }
    }

    /** Updates only sync_status and gcal_event_id for a given meeting row. */
    fun updateSyncStatus(id: Long, status: SyncStatus, gcalEventId: String?) {
        val values = ContentValues().apply {
            put("sync_status", status.storedValue)
            put("gcal_event_id", gcalEventId)
        }
        helper.writableDatabase.update(
            DaybookDatabase.TABLE_MEETINGS,
            values,
            WHERE_ID,
            arrayOf(id.toString()),
        )
    }

    /**
     * Deleting a meeting leaves its action items behind as standalone tasks,
     * because `tasks.meeting_id` is declared ON DELETE SET NULL. Work you
     * committed to does not disappear just because the meeting record does.
     */
    fun delete(id: Long): Int =
        helper.writableDatabase.delete(
            DaybookDatabase.TABLE_MEETINGS,
            WHERE_ID,
            arrayOf(id.toString()),
        )

    fun deleteAll(): Int =
        helper.writableDatabase.delete(DaybookDatabase.TABLE_MEETINGS, null, null)

    private fun Meeting.toContentValues(): ContentValues = ContentValues().apply {
        put("title", title)
        put("attendees", attendees)
        put("day", Dates.store(day))
        put("start_time", Dates.store(startTime))
        put("location", location)
        put("notes", notes)
        put("next_touch", Dates.store(nextTouch))
        put("follow_up_done", if (followUpDone) 1 else 0)
        put("gcal_event_id", gcalEventId)
        put("sync_status", syncStatus.storedValue)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun readMeeting(cursor: Cursor): Meeting = Meeting(
        id = cursor.reqLong("id"),
        title = cursor.reqString("title"),
        attendees = cursor.reqString("attendees"),
        day = Dates.parseDate(cursor.reqString("day")) ?: LocalDate.EPOCH,
        startTime = Dates.parseTime(cursor.optString("start_time")),
        location = cursor.reqString("location"),
        notes = cursor.reqString("notes"),
        nextTouch = Dates.parseDate(cursor.optString("next_touch")),
        followUpDone = cursor.reqBoolean("follow_up_done"),
        createdAt = cursor.reqLong("created_at"),
        updatedAt = cursor.reqLong("updated_at"),
        gcalEventId = cursor.optString("gcal_event_id"),
        syncStatus = SyncStatus.fromStored(cursor.optString("sync_status")),
    )

    private companion object {
        const val WHERE_ID = "id = ?"

        // Most recent meeting first; nulls in start_time sort last within a day.
        const val SELECT_ALL = """
            SELECT * FROM meetings
            ORDER BY
                day DESC,
                CASE WHEN start_time IS NULL THEN 1 ELSE 0 END ASC,
                start_time DESC,
                created_at DESC
        """

        const val SELECT_PENDING_SYNC = """
            SELECT * FROM meetings WHERE sync_status = 'PENDING_SYNC'
        """
    }
}
