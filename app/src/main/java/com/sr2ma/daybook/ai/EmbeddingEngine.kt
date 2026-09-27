package com.sr2ma.daybook.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer

/**
 * On-device text embedding using ONNX MiniLM-L6-v2 INT8.
 *
 * The model file (`minilm-l6-v2-int8.onnx`) must be placed in `assets/`.
 * It is bundled in the APK (~22 MB) and is never downloaded at runtime.
 *
 * Uses a real offline WordPiece subword tokenizer ([WordPieceTokenizer])
 * with the bundled vocabulary (`assets/vocab.txt`) ensuring mathematically
 * valid BERT embeddings.
 *
 * Output: a 384-dimensional L2-normalised float array, ready for cosine-
 * similarity via sqlite-vec or pure Kotlin fallback KNN queries.
 */
class EmbeddingEngine(
    private val context: Context,
    customTokenizer: WordPieceTokenizer? = null,
) {

    val tokenizer: WordPieceTokenizer = customTokenizer ?: WordPieceTokenizer.fromContext(context)
    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns a 384-dim L2-normalised float embedding for [text], or null if
     * the ONNX session cannot be initialised (e.g. model asset missing).
     */
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.Default) {
        try {
            val s = getOrCreateSession() ?: return@withContext null
            val env = ortEnv ?: return@withContext null

            val (inputIds, attentionMask, tokenTypeIds) = tokenizer.tokenize(text.take(MAX_CHARS), MAX_TOKENS)

            val len = inputIds.size.toLong()
            val shape = longArrayOf(1L, len)

            val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
            val maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)
            val typeIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape)

            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to maskTensor,
                "token_type_ids" to typeIdsTensor,
            )

            val result = s.run(inputs)
            // MiniLM output: [1, seq_len, 384] — mean-pool over seq dimension
            val output = (result[0].value as Array<*>)[0] as Array<*>
            val pooled = meanPool(output, attentionMask)
            val normalised = l2Normalise(pooled)

            inputIdsTensor.close(); maskTensor.close(); typeIdsTensor.close()
            result.close()

            normalised
        } catch (e: Exception) {
            null
        }
    }

    /** Releases ONNX resources. Call when the embedding engine is no longer needed. */
    fun close() {
        try { session?.close() } catch (_: Exception) {}
        try { ortEnv?.close() } catch (_: Exception) {}
        session = null
        ortEnv = null
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun getOrCreateSession(): OrtSession? {
        session?.let { return it }
        val modelBytes = runCatching {
            context.assets.open(MODEL_ASSET).readBytes()
        }.getOrElse {
            return null
        }
        val env = OrtEnvironment.getEnvironment().also { ortEnv = it }
        val opts = OrtSession.SessionOptions()
        return env.createSession(modelBytes, opts).also { session = it }
    }

    /** Mean-pools the [embeddings] sequence over unmasked positions. */
    private fun meanPool(embeddings: Array<*>, attentionMask: LongArray): FloatArray {
        val dim = EMBEDDING_DIM
        val result = FloatArray(dim)
        var count = 0
        for (i in attentionMask.indices) {
            if (attentionMask[i] == 0L) continue
            val vec = embeddings[i] as FloatArray
            for (j in 0 until dim) result[j] += vec[j]
            count++
        }
        if (count > 0) for (j in 0 until dim) result[j] /= count
        return result
    }

    /** In-place L2 normalisation. */
    private fun l2Normalise(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = Math.sqrt(norm.toDouble()).toFloat()
        if (norm > 0f) for (i in v.indices) v[i] /= norm
        return v
    }

    companion object {
        private const val TAG = "EmbeddingEngine"
        private const val MODEL_ASSET = "minilm-l6-v2-int8.onnx"
        private const val EMBEDDING_DIM = 384
        private const val MAX_TOKENS = 128
        private const val MAX_CHARS = 512
        const val CLS_TOKEN = 101L
        const val SEP_TOKEN = 102L
        const val VOCAB_SIZE = 30522L
    }
}
