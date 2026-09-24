package com.sr2ma.daybook.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.mapRows
import com.sr2ma.daybook.data.reqLong
import com.sr2ma.daybook.data.reqString
import com.sr2ma.daybook.domain.model.ConversationSummary

/**
 * DAO for the `conversation_summaries` table.
 *
 * Cache-tier: at most 50 rows (ring buffer). All queries are parameterized.
 * No SQL is built from user input.
 *
 * Follows the same [DaybookDatabase]-holding pattern as [WhatsAppDao].
 */
class ConversationDao(private val db: DaybookDatabase) {

    private val TABLE = DaybookDatabase.TABLE_CONVERSATION_SUMMARIES
    private val MAX_ROWS = 50

    /**
     * Inserts a summary row and immediately prunes the ring buffer to [MAX_ROWS]
     * so the table never grows beyond ~10 KB.
     */
    fun insertSummary(summary: ConversationSummary) {
        val cv = ContentValues().apply {
            put("summary", summary.summary.take(200))
            put("entities", summary.entities)
            put("created_at", summary.createdAt)
        }
        db.writableDatabase.insert(TABLE, null, cv)
        pruneOldest(MAX_ROWS)
    }

    /**
     * Returns the [limit] most recent summaries, newest first.
     */
    fun recentSummaries(limit: Int = 3): List<ConversationSummary> =
        db.readableDatabase.rawQuery(
            "SELECT id, summary, entities, created_at FROM $TABLE ORDER BY created_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).mapRows(::fromCursor)

    /**
     * Deletes rows so only the newest [keepCount] rows remain.
     * Called automatically by [insertSummary].
     */
    fun pruneOldest(keepCount: Int = MAX_ROWS) {
        db.writableDatabase.execSQL(
            "DELETE FROM $TABLE WHERE id NOT IN " +
                "(SELECT id FROM $TABLE ORDER BY created_at DESC LIMIT ?)",
            arrayOf<Any>(keepCount),
        )
    }

    /** Deletes all rows. Called from Settings → "Clear conversation history". */
    fun deleteAll() {
        db.writableDatabase.delete(TABLE, null, null)
    }

    /** Returns the current row count. */
    fun count(): Int {
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun fromCursor(c: Cursor) = ConversationSummary(
        id = c.reqLong("id"),
        summary = c.reqString("summary"),
        entities = c.reqString("entities"),
        createdAt = c.reqLong("created_at"),
    )
}
