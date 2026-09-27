package com.sr2ma.daybook.ai

import android.database.sqlite.SQLiteDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * KNN semantic search over task/log/meeting text via sqlite-vec with pure Kotlin fallback.
 *
 * If `libsqlitevec` is present in `jniLibs/arm64-v8a/`, native hardware-accelerated KNN
 * is performed using the `vec0` virtual table. If the native extension is absent or fails
 * (e.g. on host JVM tests, x86 emulators, or unsupported architectures), [VectorStore]
 * automatically and seamlessly falls back to pure Kotlin KNN cosine similarity search over
 * stored SQLite float BLOBs in `embeddings_fallback`.
 *
 * Semantic search never fails or throws an unhandled [android.database.sqlite.SQLiteException].
 */
class VectorStore(private val database: SQLiteDatabase) {

    /**
     * Whether the native `libsqlitevec` extension is currently loaded and available.
     * If false, pure Kotlin cosine similarity fallback is used.
     */
    var isNativeAvailable: Boolean = tryLoadExtension()
        private set

    /**
     * Always true: semantic search is always available either via native sqlite-vec
     * or via the resilient pure Kotlin cosine similarity search.
     */
    val isAvailable: Boolean get() = true

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Creates the vector tables.
     * Ensures both `embeddings` (if native is available) and `embeddings_fallback` are ready.
     */
    fun ensureSchema() {
        if (isNativeAvailable) {
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
            } catch (_: Exception) {
                isNativeAvailable = false
            }
        }

        try {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS embeddings_fallback (
                    item_id   INTEGER NOT NULL,
                    item_type TEXT NOT NULL,
                    embedding BLOB NOT NULL,
                    PRIMARY KEY (item_id, item_type)
                )
                """.trimIndent(),
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Inserts or replaces the embedding for [itemId]/[itemType].
     *
     * @param itemId   Row ID from the source table (tasks.id, meetings.id, etc.).
     * @param itemType One of: "task", "log", "meeting", "pass".
     * @param embedding 384-dim L2-normalised float array from [EmbeddingEngine].
     */
    fun upsert(itemId: Long, itemType: String, embedding: FloatArray) {
        if (embedding.isEmpty()) return
        val blob = serializeVec(embedding)

        if (isNativeAvailable) {
            try {
                database.execSQL(
                    "INSERT OR REPLACE INTO embeddings(item_id, item_type, embedding) VALUES(?, ?, ?)",
                    arrayOf<Any?>(itemId, itemType, blob),
                )
                // Dual-write to fallback table so queries never lose data if native degrades
                database.execSQL(
                    "INSERT OR REPLACE INTO embeddings_fallback(item_id, item_type, embedding) VALUES(?, ?, ?)",
                    arrayOf<Any?>(itemId, itemType, blob),
                )
                return
            } catch (_: Exception) {
                isNativeAvailable = false
            }
        }

        try {
            database.execSQL(
                "INSERT OR REPLACE INTO embeddings_fallback(item_id, item_type, embedding) VALUES(?, ?, ?)",
                arrayOf<Any?>(itemId, itemType, blob),
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Deletes the embedding for [itemId]/[itemType].
     */
    fun delete(itemId: Long, itemType: String) {
        if (isNativeAvailable) {
            try {
                database.execSQL(
                    "DELETE FROM embeddings WHERE item_id = ? AND item_type = ?",
                    arrayOf<Any?>(itemId, itemType),
                )
            } catch (_: Exception) {
                isNativeAvailable = false
            }
        }

        try {
            database.execSQL(
                "DELETE FROM embeddings_fallback WHERE item_id = ? AND item_type = ?",
                arrayOf<Any?>(itemId, itemType),
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Returns up to [k] nearest neighbours to [queryEmbedding] across all [itemType]s,
     * ordered by cosine distance (ascending).
     *
     * Never throws [android.database.sqlite.SQLiteException]; automatically falls back
     * to pure Kotlin cosine similarity if native search fails or is unavailable.
     */
    fun knn(queryEmbedding: FloatArray, k: Int = 10, itemType: String? = null): List<KnnResult> {
        if (queryEmbedding.isEmpty() || k <= 0) return emptyList()

        if (isNativeAvailable) {
            try {
                val typeFilter = if (itemType != null) "AND item_type = ?" else ""
                val hexVec = serializeVec(queryEmbedding).joinToString("") { "%02x".format(it) }
                val inlineSql = """
                    SELECT item_id, item_type, distance
                      FROM embeddings
                     WHERE embedding MATCH X'$hexVec'
                       AND k = $k
                       $typeFilter
                     ORDER BY distance
                """.trimIndent()
                val stringArgs: Array<String?> = if (itemType != null) arrayOf(itemType) else arrayOf()
                val nativeResults = database.rawQuery(inlineSql, stringArgs).use { cursor ->
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
                if (nativeResults.isNotEmpty()) {
                    return nativeResults
                }
            } catch (_: Exception) {
                isNativeAvailable = false
            }
        }

        // Pure Kotlin fallback KNN search over stored SQLite float BLOBs
        return knnFallback(queryEmbedding, k, itemType)
    }

    /**
     * Performs pure Kotlin KNN cosine distance search across all candidate BLOBs in `embeddings_fallback`.
     */
    fun knnFallback(queryEmbedding: FloatArray, k: Int = 10, itemType: String? = null): List<KnnResult> {
        return try {
            val typeFilter = if (itemType != null) "WHERE item_type = ?" else ""
            val stringArgs = if (itemType != null) arrayOf(itemType) else arrayOf()
            val sql = "SELECT item_id, item_type, embedding FROM embeddings_fallback $typeFilter"

            val candidates = ArrayList<KnnResult>()
            database.rawQuery(sql, stringArgs).use { cursor ->
                val idIndex = cursor.getColumnIndex("item_id")
                val typeIndex = cursor.getColumnIndex("item_type")
                val blobIndex = cursor.getColumnIndex("embedding")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val type = cursor.getString(typeIndex)
                    val blob = cursor.getBlob(blobIndex) ?: continue
                    val vec = deserializeVec(blob)
                    val dist = cosineDistance(queryEmbedding, vec)
                    candidates.add(KnnResult(itemId = id, itemType = type, distance = dist))
                }
            }
            candidates.sortedBy { it.distance }.take(k)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Vector serialization and math ─────────────────────────────────────────

    private fun tryLoadExtension(): Boolean = try {
        database.execSQL("SELECT load_extension('libsqlitevec')")
        true
    } catch (_: Exception) {
        false
    }

    fun cosineDistance(a: FloatArray, b: FloatArray): Float = Companion.cosineDistance(a, b)

    fun serializeVec(v: FloatArray): ByteArray = Companion.serializeVec(v)

    fun deserializeVec(bytes: ByteArray): FloatArray = Companion.deserializeVec(bytes)

    data class KnnResult(val itemId: Long, val itemType: String, val distance: Float)

    companion object {
        private const val TAG = "VectorStore"

        /**
         * Computes the cosine distance between two float vectors.
         * Distance is `1.0 - cosine_similarity`, in range [0.0, 2.0].
         */
        fun cosineDistance(a: FloatArray, b: FloatArray): Float {
            val len = minOf(a.size, b.size)
            if (len == 0) return 1f
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in 0 until len) {
                val x = a[i]
                val y = b[i]
                dot += x * y
                normA += x * x
                normB += y * y
            }
            if (normA <= 0f || normB <= 0f) return 1f
            val similarity = dot / (sqrt(normA) * sqrt(normB))
            return (1f - similarity).coerceIn(0f, 2f)
        }

        /**
         * Serialises a float array to little-endian IEEE 754 float bytes (4 bytes per float).
         */
        fun serializeVec(v: FloatArray): ByteArray {
            val buf = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            v.forEach { buf.putFloat(it) }
            return buf.array()
        }

        /**
         * Deserialises little-endian IEEE 754 float bytes into a FloatArray.
         */
        fun deserializeVec(bytes: ByteArray): FloatArray {
            val count = bytes.size / 4
            val result = FloatArray(count)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) {
                result[i] = buf.float
            }
            return result
        }
    }
}
