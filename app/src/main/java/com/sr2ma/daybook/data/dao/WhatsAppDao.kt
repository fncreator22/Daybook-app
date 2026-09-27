package com.sr2ma.daybook.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.mapRows
import com.sr2ma.daybook.data.optLong
import com.sr2ma.daybook.data.optString
import com.sr2ma.daybook.data.reqLong
import com.sr2ma.daybook.data.reqString
import com.sr2ma.daybook.domain.model.WhatsAppMessage

class WhatsAppDao(private val db: DaybookDatabase) {

    fun insert(msg: WhatsAppMessage): Long {
        val cv = ContentValues().apply {
            put("sender", msg.sender)
            put("message", msg.message)
            put("received_at", msg.receivedAt)
            msg.repliedText?.let  { put("replied_text", it) }
            msg.repliedAt?.let    { put("replied_at", it) }
            msg.notificationKey?.let { put("notification_key", it) }
            put("created_at", msg.createdAt)
        }
        return db.writableDatabase.insert(DaybookDatabase.TABLE_WHATSAPP, null, cv)
    }

    fun updateReply(id: Long, replyText: String, repliedAt: Long) {
        val cv = ContentValues().apply {
            put("replied_text", replyText)
            put("replied_at", repliedAt)
        }
        db.writableDatabase.update(
            DaybookDatabase.TABLE_WHATSAPP, cv,
            "id = ?", arrayOf(id.toString()),
        )
    }

    /** Most-recent N messages across all senders, for Today board. */
    fun recentMessages(limit: Int = 20): List<WhatsAppMessage> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM ${DaybookDatabase.TABLE_WHATSAPP} ORDER BY received_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).mapRows(::fromCursor)

    /** All messages from a specific sender, newest first. */
    fun messagesFrom(sender: String, limit: Int = 50): List<WhatsAppMessage> =
        db.readableDatabase.rawQuery(
            "SELECT * FROM ${DaybookDatabase.TABLE_WHATSAPP} WHERE sender = ? ORDER BY received_at DESC LIMIT ?",
            arrayOf(sender, limit.toString()),
        ).mapRows(::fromCursor)

    /** Unique senders seen since [sinceEpochMs], ordered by most recent message. */
    fun recentSenders(sinceEpochMs: Long): List<String> {
        val result = mutableListOf<String>()
        db.readableDatabase.rawQuery(
            "SELECT DISTINCT sender FROM ${DaybookDatabase.TABLE_WHATSAPP} WHERE received_at >= ? ORDER BY MAX(received_at) DESC",
            arrayOf(sinceEpochMs.toString()),
        ).use { c -> while (c.moveToNext()) result.add(c.getString(0)) }
        return result
    }

    fun delete(id: Long): Int = db.writableDatabase.delete(
        DaybookDatabase.TABLE_WHATSAPP, "id = ?", arrayOf(id.toString())
    )

    fun deleteAll(): Int = db.writableDatabase.delete(DaybookDatabase.TABLE_WHATSAPP, null, null)

    private fun fromCursor(c: Cursor) = WhatsAppMessage(
        id              = c.reqLong("id"),
        sender          = c.reqString("sender"),
        message         = c.reqString("message"),
        receivedAt      = c.reqLong("received_at"),
        repliedText     = c.optString("replied_text"),
        repliedAt       = c.optLong("replied_at"),
        notificationKey = c.optString("notification_key"),
        createdAt       = c.reqLong("created_at"),
    )
}
