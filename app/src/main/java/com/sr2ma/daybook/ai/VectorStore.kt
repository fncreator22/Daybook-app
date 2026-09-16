package com.sr2ma.daybook.ai

import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * KNN semantic search over task/log/meeting text via sqlite-vec.
 *
 * sqlite-vec loads as a SQLite extension from the pre-compiled `.so` in
 * `jniLibs/arm64-v8a/`. If the library is absent, [isAvailable] returns
 * false and all methods no-op / return empty results — graceful degradation.
 *
 * Schema (created by [ensureSchema]):
 * ```sql
 * CREATE VIRTUAL TABLE embeddings USING vec0(
 *     item_id   INTEGER PRIMARY KEY,
 *     item_type TEXT NOT NULL,    -- 'task' | 'log' | 'meeting' | 'pass'
 *     embedding FLOAT[384]        -- L2-normalised MiniLM vector
 * )
 * ```
 *
 * KNN query example:
 * ```sql
 * SELECT item_id, item_type, distance
 *   FROM embeddings
 *  WHERE embedding MATCH ?  -- serialised query vector
 *    AND k = 10
 *  ORDER BY distance;
 * ```
 */
class VectorStore(private val database: SQLiteDatabase) {

    /**
     * True if the sqlite-vec extension loaded successfully.
     * False if the `.so` is absent (graceful degradation — no semantic search).
     */
    val isAvailable: Boolean = tryLoadExtension()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates the `embeddings` virtual table if not already present.
     * No-ops if [isAvailable] is false.
     */
    fun ensureSchema() {
        if (!isAvailable) return
        try {
            database.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS embeddings USING vec0(
                    item_id   INTEGER PRIMARY KEY,
                    item_type TEXT NOT NULL,
                    embedding FLOAT[384]
                )
                """.trimIndent(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create embeddings table", e)
        }
    }

    /**
     * Inserts or replaces the embedding for [itemId]/[itemType].
     * No-ops if [isAvailable] is false or [embedding] is null.
     *
     * @param itemId   Row ID from the source table (tasks.id, meetings.id, etc.).
     * @param itemType One of: "task", "log", "meeting", "pass".
     * @param embedding 384-dim L2-normalised float array from [EmbeddingEngine].
     */
    fun upsert(itemId: Long, itemType: String, embedding: FloatArray) {
        if (!isAvailable) return
        try {
            database.execSQL(
                "INSERT OR REPLACE INTO embeddings(item_id, item_type, embedding) VALUES(?, ?, ?)",
                arrayOf(itemId, itemType, serializeVec(embedding)),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upsert embedding id=$itemId type=$itemType", e)
        }
    }

    /**
     * Deletes the embedding for [itemId]/[itemType] (call when the source row is deleted).
     */
    fun delete(itemId: Long, itemType: String) {
        if (!isAvailable) return
        try {
            database.execSQL(
                "DELETE FROM embeddings WHERE item_id = ? AND item_type = ?",
                arrayOf(itemId, itemType),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete embedding id=$itemId", e)
        }
    }

    /**
     * Returns up to [k] nearest neighbours to [queryEmbedding] across all [itemType]s,
     * ordered by cosine distance (ascending). Returns empty list if [isAvailable] is false.
     */
    fun knn(queryEmbedding: FloatArray, k: Int = 10, itemType: String? = null): List<KnnResult> {
        if (!isAvailable) return emptyList()
        return try {
            val typeFilter = if (itemType != null) "AND item_type = ?" else ""
            val sql = """
                SELECT item_id, item_type, distance
                  FROM embeddings
                 WHERE embedding MATCH ?
                   AND k = $k
                   $typeFilter
                 ORDER BY distance
            """.trimIndent()
            val args = if (itemType != null) {
                arrayOf(serializeVec(queryEmbedding), itemType)
            } else {
                arrayOf(serializeVec(queryEmbedding))
            }
            database.rawQuery(sql, args).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            KnnResult(
                                itemId = cursor.getLong(0),
                                itemType = cursor.getString(1),
                                distance = cursor.getFloat(2),
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "KNN query failed", e)
            emptyList()
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun tryLoadExtension(): Boolean = try {
        database.execSQL("SELECT load_extension('libsqlitevec')")
        true
    } catch (e: Exception) {
        Log.w(TAG, "sqlite-vec extension not available — semantic search disabled: ${e.message}")
        false
    }

    /**
     * Serialises a float array to the binary format expected by sqlite-vec:
     * little-endian IEEE 754 floats, 4 bytes each.
     */
    private fun serializeVec(v: FloatArray): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(v.size * 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        v.forEach { buf.putFloat(it) }
        return buf.array()
    }

    data class KnnResult(val itemId: Long, val itemType: String, val distance: Float)

    companion object {
        private const val TAG = "VectorStore"
    }
}
