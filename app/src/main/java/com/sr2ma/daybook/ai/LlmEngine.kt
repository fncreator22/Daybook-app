package com.sr2ma.daybook.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.LlmInference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Singleton holder for the LiteRT-LM inference engine.
 *
 * AGENTS.md non-negotiables — all enforced:
 * ─────────────────────────────────────────
 * 1. LAZY-LOAD: [Engine] is created on first AI-feature use. Never at app start.
 * 2. IDLE RELEASE: If no [infer] call arrives for 3 minutes, the engine is
 *    closed and null-ed. The next call re-creates it transparently.
 * 3. SINGLE QUEUE: One [Mutex] guards all inference. Concurrent calls queue,
 *    never run in parallel (ADR-0004: drop policy is in AgentEngine above this).
 * 4. HARD TIMEOUT: 30-second [withTimeout] on every [infer] call. Exceeded
 *    inference counts as a failure — the engine is released and the caller
 *    receives a [InferResult.Timeout].
 *
 * Usage:
 * ```kotlin
 * val llm = LlmEngine(context, downloader)
 * when (val r = llm.infer("Classify: buy milk tomorrow")) {
 *     is InferResult.Success -> use(r.text)
 *     is InferResult.Timeout -> showError("AI took too long")
 *     is InferResult.ModelNotReady -> promptDownload()
 *     is InferResult.Failure -> log(r.cause)
 * }
 * ```
 */
class LlmEngine(
    private val context: Context,
    private val downloader: ModelDownloader,
    private val versionTag: String = "v1",
) {

    sealed interface InferResult {
        data class Success(val text: String) : InferResult
        data object Timeout : InferResult
        data object ModelNotReady : InferResult
        data class Failure(val cause: Throwable) : InferResult
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private val mutex = Mutex()
    private var engine: LlmInference? = null
    private val lastUseMs = AtomicLong(0L)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs [prompt] through the LLM and returns the generated text.
     *
     * Serialised by [mutex]: callers queue. Never runs two inferences in
     * parallel. Times out after [TIMEOUT_MS].
     */
    suspend fun infer(prompt: String): InferResult = mutex.withLock {
        lastUseMs.set(System.currentTimeMillis())

        val modelFile = downloader.modelFile(versionTag)
        if (!modelFile.exists()) return@withLock InferResult.ModelNotReady

        try {
            val llm = getOrCreate(modelFile)
            withTimeout(TIMEOUT_MS) {
                val result = llm.generateResponse(prompt)
                lastUseMs.set(System.currentTimeMillis())
                InferResult.Success(result)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "Inference timed out after ${TIMEOUT_MS}ms — releasing engine")
            releaseEngine()
            InferResult.Timeout
        } catch (e: CancellationException) {
            throw e   // propagate coroutine cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            releaseEngine()
            InferResult.Failure(e)
        }
    }

    /**
     * Checks if the idle timeout has expired and releases the engine if so.
     * Call this from a periodic coroutine (e.g. every 30 s) in the ViewModel
     * or a background worker.
     */
    fun tickIdleCheck() {
        val idle = System.currentTimeMillis() - lastUseMs.get()
        if (engine != null && idle >= IDLE_RELEASE_MS) {
            Log.d(TAG, "LLM engine idle for ${idle / 1000}s — releasing")
            releaseEngine()
        }
    }

    /** Immediately releases the engine. Safe to call when the app goes to background. */
    fun release() = releaseEngine()

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the cached engine or creates a new one.
     * Must be called while [mutex] is held.
     */
    private fun getOrCreate(modelFile: File): LlmInference {
        engine?.let { return it }
        Log.d(TAG, "Creating LlmInference engine from ${modelFile.name}")
        val options = LlmInference.Options.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .build()
        return LlmInference.createFromOptions(context, options).also { engine = it }
    }

    private fun releaseEngine() {
        try { engine?.close() } catch (_: Exception) { }
        engine = null
    }

    companion object {
        private const val TAG = "LlmEngine"
        private const val TIMEOUT_MS = 30_000L          // AGENTS.md hard requirement
        private const val IDLE_RELEASE_MS = 3 * 60_000L // AGENTS.md: 3-minute idle release
        private const val MAX_TOKENS = 512
    }
}
