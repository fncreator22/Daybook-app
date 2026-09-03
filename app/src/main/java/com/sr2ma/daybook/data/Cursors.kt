package com.sr2ma.daybook.data

import android.database.Cursor

/**
 * Small read helpers so the DAOs stay about SQL rather than column bookkeeping.
 * [Cursor.getColumnIndexOrThrow] means a rename in the schema fails loudly on the
 * first read instead of silently returning the wrong column.
 */

internal fun Cursor.reqString(column: String): String =
    getString(getColumnIndexOrThrow(column)) ?: ""

internal fun Cursor.optString(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

internal fun Cursor.reqLong(column: String): Long = getLong(getColumnIndexOrThrow(column))

internal fun Cursor.optLong(column: String): Long? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getLong(index)
}

internal fun Cursor.reqInt(column: String): Int = getInt(getColumnIndexOrThrow(column))

internal fun Cursor.reqBoolean(column: String): Boolean = reqInt(column) != 0

/** Reads every row with [read], always closing the cursor. */
internal inline fun <T> Cursor.mapRows(read: (Cursor) -> T): List<T> = use { cursor ->
    val results = ArrayList<T>(cursor.count)
    while (cursor.moveToNext()) {
        results.add(read(cursor))
    }
    results
}
