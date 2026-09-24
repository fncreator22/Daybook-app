package com.sr2ma.daybook.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.mapRows
import com.sr2ma.daybook.data.optLong
import com.sr2ma.daybook.data.optString
import com.sr2ma.daybook.data.reqLong
import com.sr2ma.daybook.data.reqString
import com.sr2ma.daybook.domain.model.GmailMessage

class GmailDao(private val db: DaybookDatabase) {

    fun insertOrIgnore(msg: GmailMessage): Long {
        val cv = ContentValues().apply {
            put("message_id", msg.messageId)
            put("sender", msg.sender)
            put("subject", msg.subject)
            put("snippet", msg.snippet)
            put("received_at", msg.receivedAt)
            put("is_read", if (msg.isRead) 1 else 0)
            put("category", msg.category)
            msg.suggestedAction?.let { put("suggested_action", it) }
            msg.actionedAt?.let { put("actioned_at", it) }
        }
        return db.writableDatabase.insertWithOnConflict(
            DaybookDatabase.TABLE_GMAIL,
            null,
            cv,
            android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun markActioned(id: Long) {
        val cv = ContentValues().apply {
            put("actioned_at", System.currentTimeMillis())
        }
        db.writableDatabase.update(
            DaybookDatabase.TABLE_GMAIL,
            cv,
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    /**
     * Recent emails for display.
     * When [filterSpamAndPromo] is true, excludes messages categorized as 'spam' or 'promotions'.
     */
    fun recentMessages(limit: Int = 20, filterSpamAndPromo: Boolean = true): List<GmailMessage> {
        val where = if (filterSpamAndPromo) {
            "WHERE category NOT IN ('spam', 'promotions')"
        } else {
            ""
        }
        val sql = "SELECT * FROM ${DaybookDatabase.TABLE_GMAIL} $where ORDER BY received_at DESC LIMIT ?"
        return db.readableDatabase.rawQuery(sql, arrayOf(limit.toString())).mapRows(::fromCursor)
    }

    /** Returns messages that have an un-actioned suggested task or meeting. */
    fun actionableMessages(limit: Int = 10): List<GmailMessage> {
        val sql = """
            SELECT * FROM ${DaybookDatabase.TABLE_GMAIL} 
            WHERE suggested_action IS NOT NULL 
              AND actioned_at IS NULL 
              AND category NOT IN ('spam', 'promotions')
            ORDER BY received_at DESC LIMIT ?
        """.trimIndent()
        return db.readableDatabase.rawQuery(sql, arrayOf(limit.toString())).mapRows(::fromCursor)
    }

    fun delete(id: Long): Int =
        db.writableDatabase.delete(DaybookDatabase.TABLE_GMAIL, "id = ?", arrayOf(id.toString()))

    fun deleteAll(): Int =
        db.writableDatabase.delete(DaybookDatabase.TABLE_GMAIL, null, null)

    private fun fromCursor(c: Cursor) = GmailMessage(
        id              = c.reqLong("id"),
        messageId       = c.reqString("message_id"),
        sender          = c.reqString("sender"),
        subject         = c.reqString("subject"),
        snippet         = c.reqString("snippet"),
        receivedAt      = c.reqLong("received_at"),
        isRead          = c.reqLong("is_read") == 1L,
        category        = c.optString("category") ?: GmailMessage.CATEGORY_PRIMARY,
        suggestedAction = c.optString("suggested_action"),
        actionedAt      = c.optLong("actioned_at"),
    )
}
