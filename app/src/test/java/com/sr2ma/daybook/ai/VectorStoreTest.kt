package com.sr2ma.daybook.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorStoreTest {

    // Dummy VectorStore instance with null database just to access math and serialization methods
    // Or we test through mock/instance
    private fun createFloatVector(vararg values: Float): FloatArray = values

    @Test
    fun `serialize and deserialize roundtrip preserves float values`() {
        val original = floatArrayOf(0.123f, -0.456f, 0.789f, 1.0f, -1.0f, 0.0f)
        val serialized = VectorStore.serializeVec(original)
        val deserialized = VectorStore.deserializeVec(serialized)

        assertEquals(original.size, deserialized.size)
        for (i in original.indices) {
            assertEquals(original[i], deserialized[i], 0.00001f)
        }
    }

    @Test
    fun `cosine distance is zero for identical vectors`() {
        val a = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val b = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val dist = VectorStore.cosineDistance(a, b)
        assertEquals(0f, dist, 0.0001f)
    }

    @Test
    fun `cosine distance is one for orthogonal vectors`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val dist = VectorStore.cosineDistance(a, b)
        assertEquals(1f, dist, 0.0001f)
    }

    @Test
    fun `cosine distance correctly ranks nearest neighbours`() {
        val query = floatArrayOf(1f, 0.9f, 0.1f)
        val candidate1 = floatArrayOf(0.95f, 0.88f, 0.12f) // very close
        val candidate2 = floatArrayOf(0.2f, 0.1f, 0.9f)   // very different
        val candidate3 = floatArrayOf(0.8f, 0.7f, 0.2f)   // moderately close

        val d1 = VectorStore.cosineDistance(query, candidate1)
        val d2 = VectorStore.cosineDistance(query, candidate2)
        val d3 = VectorStore.cosineDistance(query, candidate3)

        assertTrue("Candidate 1 should be closest to query", d1 < d3)
        assertTrue("Candidate 3 should be closer than candidate 2", d3 < d2)
    }
}
