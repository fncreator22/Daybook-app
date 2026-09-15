package com.sr2ma.daybook.data.dao

import android.content.ContentValues
import android.database.Cursor
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.mapRows
import com.sr2ma.daybook.data.optLong
import com.sr2ma.daybook.data.optString
import com.sr2ma.daybook.data.reqLong
import com.sr2ma.daybook.data.reqString
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.PassCategory

/** All reads and writes for the `passes` table. */
class PassDao(private val helper: DaybookDatabase) {

    fun all(): List<Pass> =
        helper.readableDatabase.rawQuery(SELECT_ALL, null).mapRows(::readPass)

    fun byId(id: Long): Pass? =
        helper.readableDatabase
            .rawQuery(SELECT_BY_ID, arrayOf(id.toString()))
            .mapRows(::readPass)
            .firstOrNull()

    fun byCategory(category: PassCategory): List<Pass> =
        helper.readableDatabase
            .rawQuery(SELECT_BY_CATEGORY, arrayOf(category.storedValue))
            .mapRows(::readPass)

    fun upsert(pass: Pass): Long {
        val db = helper.writableDatabase
        val values = pass.toContentValues()
        if (pass.id == 0L) {
            return db.insertOrThrow(DaybookDatabase.TABLE_PASSES, null, values)
        }
        val updated = db.update(
            DaybookDatabase.TABLE_PASSES,
            values,
            WHERE_ID,
            arrayOf(pass.id.toString()),
        )
        return if (updated > 0) pass.id
        else db.insertOrThrow(DaybookDatabase.TABLE_PASSES, null, values)
    }

    fun delete(id: Long): Int =
        helper.writableDatabase.delete(DaybookDatabase.TABLE_PASSES, WHERE_ID, arrayOf(id.toString()))

    fun deleteAll(): Int =
        helper.writableDatabase.delete(DaybookDatabase.TABLE_PASSES, null, null)

    private fun Pass.toContentValues(): ContentValues = ContentValues().apply {
        put("title", title)
        put("category", category.storedValue)
        put("barcode_value", barcodeValue)
        put("barcode_format", barcodeFormat)
        put("ocr_text", ocrText)
        put("notes", notes)
        put("expiry_date", Dates.store(expiryDate))
        put("balance", balance)
        put("image_path", imagePath)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun readPass(cursor: Cursor): Pass = Pass(
        id = cursor.reqLong("id"),
        title = cursor.reqString("title"),
        category = PassCategory.fromStored(cursor.reqString("category")),
        barcodeValue = cursor.reqString("barcode_value"),
        barcodeFormat = cursor.reqString("barcode_format"),
        ocrText = cursor.reqString("ocr_text"),
        notes = cursor.reqString("notes"),
        expiryDate = Dates.parseDate(cursor.optString("expiry_date")),
        balance = cursor.optString("balance"),
        imagePath = cursor.optString("image_path"),
        createdAt = cursor.reqLong("created_at"),
        updatedAt = cursor.reqLong("updated_at"),
    )

    private companion object {
        const val WHERE_ID = "id = ?"
        const val SELECT_ALL = """
            SELECT * FROM passes ORDER BY updated_at DESC
        """
        const val SELECT_BY_ID = """
            SELECT * FROM passes WHERE id = ?
        """
        const val SELECT_BY_CATEGORY = """
            SELECT * FROM passes WHERE category = ? ORDER BY updated_at DESC
        """
    }
}
