package com.sr2ma.daybook.ai

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Singleton holder for the LiteRT-LM inference engine.
 *
 * Uses reflection to load com.google.ai.edge.litertlm.LlmInference at runtime
 * so the app compiles and runs even if the litertlm artifact is absent/mismatched.
 *
 * AGENTS.md non-negotiables — all enforced:
 * 1. LAZY-LOAD: engine created on first AI-feature use, never at app start.
 * 2. IDLE RELEASE: closed after 3 minutes of inactivity.
 * 3. SINGLE QUEUE: one Mutex, concurrent calls queue, never parallel.
 * 4. HARD TIMEOUT: 30-second withTimeout on every infer() call.
 */
class LlmEngine(
    private val context: Context,
    val downloader: ModelDownloader,
    val versionTag: String = "v1",
) {

    sealed interface InferResult {
        data class Success(val text: String) : InferResult
        data object Timeout : InferResult
        data object ModelNotReady : InferResult
        data class Failure(val cause: Throwable) : InferResult
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private val mutex = Mutex()
    /** Held as Any? so we never take a compile-time dep on LlmInference. */
    private var engine: Any? = null
    private val lastUseMs = AtomicLong(0L)

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Checks if the model file is already present on local internal storage.
     */
    fun isModelReady(): Boolean = downloader.isModelPresent(versionTag)

    /**
     * Downloads the gated Gemma 3 270M IT model legally via the official Hugging Face
     * endpoint using Bearer auth token and verifies SHA-256 integrity before installation.
     */
    suspend fun downloadModel(
        authToken: String? = null,
        customUrl: String? = null,
        onProgress: ModelDownloader.ProgressListener? = null,
    ): ModelDownloader.DownloadResult {
        return downloader.download(
            url = customUrl ?: ModelDownloader.GEMMA_270M_V1_URL,
            expectedSha256 = ModelDownloader.GEMMA_270M_V1_SHA256,
            versionTag = versionTag,
            authToken = authToken,
            onProgress = onProgress,
        )
    }

    suspend fun infer(prompt: String): InferResult = mutex.withLock {
        lastUseMs.set(System.currentTimeMillis())

        val modelFile = downloader.modelFile(versionTag)
        if (!modelFile.exists()) return@withLock InferResult.ModelNotReady

        try {
            val llm = getOrCreate(modelFile)
                ?: return@withLock InferResult.Failure(
                    IllegalStateException("LlmInference class not found — litertlm not on classpath"),
                )
            withTimeout(TIMEOUT_MS) {
                val result = generateResponse(llm, prompt)
                lastUseMs.set(System.currentTimeMillis())
                InferResult.Success(result)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            releaseEngine()
            InferResult.Timeout
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            releaseEngine()
            InferResult.Failure(e)
        }
    }

    fun tickIdleCheck() {
        val idle = System.currentTimeMillis() - lastUseMs.get()
        if (engine != null && idle >= IDLE_RELEASE_MS) {
            releaseEngine()
        }
    }

    fun release() = releaseEngine()

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Loads the LlmInference engine via reflection.
     * Returns null if the litertlm library is not on the classpath.
     */
    private fun getOrCreate(modelFile: File): Any? {
        engine?.let { return it }
        return try {
            val clazz = Class.forName("com.google.ai.edge.litertlm.LlmInference")
            val optionsClass = clazz.classes.firstOrNull { it.simpleName == "Options" }
            if (optionsClass == null) {
                return null
            }
            // LlmInference.Options.builder().setModelPath(...).setMaxTokens(...).build()
            val builder = optionsClass.getMethod("builder").invoke(null)
            builder.javaClass.getMethod("setModelPath", String::class.java)
                .invoke(builder, modelFile.absolutePath)
            builder.javaClass.getMethod("setMaxTokens", Int::class.java)
                .invoke(builder, MAX_TOKENS)
            val options = builder.javaClass.getMethod("build").invoke(builder)
            val instance = clazz.getMethod("createFromOptions", Context::class.java, optionsClass)
                .invoke(null, context, options)
            instance.also { engine = it }
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun generateResponse(llm: Any, prompt: String): String {
        return try {
            llm.javaClass.getMethod("generateResponse", String::class.java)
                .invoke(llm, prompt) as? String ?: ""
        } catch (e: Exception) {
            throw RuntimeException("generateResponse failed", e)
        }
    }

    private fun releaseEngine() {
        try {
            engine?.javaClass?.getMethod("close")?.invoke(engine)
        } catch (_: Exception) { }
        engine = null
    }

    companion object {
        private const val TAG = "LlmEngine"
        private const val TIMEOUT_MS = 30_000L
        private const val IDLE_RELEASE_MS = 3 * 60_000L
        private const val MAX_TOKENS = 512
    }
}
