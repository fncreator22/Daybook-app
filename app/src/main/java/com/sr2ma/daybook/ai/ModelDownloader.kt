package com.sr2ma.daybook.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the Gemma 270M INT4 QAT model file to internal storage.
 *
 * AGENTS.md safety requirements — all enforced here:
 * 1. Download to `model.litertlm.tmp`, never to the real filename.
 * 2. SHA-256 verify against [expectedSha256] before rename.
 * 3. Atomic `rename()` from `.tmp` to final path.
 * 4. >250 MB free-space check before starting download.
 * 5. Versioned filename: `gemma-270m-it-q4-v{N}.litertlm` so old and new
 *    can coexist during an update transition.
 * 6. Partial `.tmp` is deleted on any failure path.
 *
 * The caller (AI settings screen) is responsible for presenting the download
 * consent prompt before calling [download]. This class does not initiate
 * anything automatically.
 */
class ModelDownloader(private val context: Context) {

    /** Progress callback: (bytesDownloaded, totalBytes). totalBytes is -1 if unknown. */
    fun interface ProgressListener {
        fun onProgress(downloaded: Long, total: Long)
    }

    sealed interface DownloadResult {
        data class Success(val modelFile: File) : DownloadResult
        data class Failure(val reason: String) : DownloadResult
    }

    /**
     * Downloads the model, verifying integrity and enforcing all safety rules.
     *
     * @param url            HTTPS URL to the `.litertlm` model file on Hugging Face.
     * @param expectedSha256 Lowercase hex SHA-256 of the expected file content.
     * @param versionTag     Short version string embedded in the filename, e.g. "v1".
     * @param onProgress     Optional progress callback (called on IO thread).
     */
    suspend fun download(
        url: String,
        expectedSha256: String,
        versionTag: String = "v1",
        onProgress: ProgressListener? = null,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val modelDir = context.filesDir
        val finalFile = File(modelDir, modelFileName(versionTag))
        val tmpFile = File(modelDir, "${modelFileName(versionTag)}.tmp")

        // ── 1. Free-space guard (AGENTS.md: >250 MB required) ────────────────
        val freeBytes = modelDir.freeSpace
        if (freeBytes < MIN_FREE_BYTES) {
            return@withContext DownloadResult.Failure(
                "Not enough storage: need at least 250 MB free, " +
                    "only ${freeBytes / MB}MB available.",
            )
        }

        // ── 2. Already downloaded? Verify and skip. ───────────────────────────
        if (finalFile.exists()) {
            return@withContext if (sha256(finalFile) == expectedSha256.lowercase()) {
                DownloadResult.Success(finalFile)
            } else {
                finalFile.delete()
                DownloadResult.Failure("Existing model file failed SHA-256 check — deleted.")
            }
        }

        // ── 3. Clean up any previous partial download ─────────────────────────
        tmpFile.delete()

        // ── 4. Download → tmp ─────────────────────────────────────────────────
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "Daybook-ModelDownloader/1.0")
            }
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext DownloadResult.Failure(
                    "Server returned HTTP ${connection.responseCode}",
                )
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        onProgress?.onProgress(downloaded, total)
                    }
                }
            }
        } catch (e: Exception) {
            tmpFile.delete()
            return@withContext DownloadResult.Failure("Download failed: ${e.message}")
        }

        // ── 5. SHA-256 verification ───────────────────────────────────────────
        val actualHash = sha256(tmpFile)
        if (actualHash != expectedSha256.lowercase()) {
            tmpFile.delete()
            return@withContext DownloadResult.Failure(
                "SHA-256 mismatch — file corrupted or wrong version.\n" +
                    "Expected: $expectedSha256\nActual:   $actualHash",
            )
        }

        // ── 6. Atomic rename tmp → final ──────────────────────────────────────
        if (!tmpFile.renameTo(finalFile)) {
            tmpFile.delete()
            return@withContext DownloadResult.Failure("Could not rename model file after download.")
        }

        DownloadResult.Success(finalFile)
    }

    /** Returns the path where the model would live, without downloading. */
    fun modelFile(versionTag: String = "v1"): File =
        File(context.filesDir, modelFileName(versionTag))

    /** True if the model file exists and is non-empty (no hash check — fast path). */
    fun isModelPresent(versionTag: String = "v1"): Boolean =
        modelFile(versionTag).let { it.exists() && it.length() > 0 }

    /** Deletes the model file and its temp artefact if present. */
    fun deleteModel(versionTag: String = "v1") {
        modelFile(versionTag).delete()
        File(context.filesDir, "${modelFileName(versionTag)}.tmp").delete()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun modelFileName(versionTag: String): String =
        "gemma-270m-it-q8-$versionTag.litertlm"

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MB = 1_024L * 1_024L
        private const val MIN_FREE_BYTES = 250L * MB   // AGENTS.md hard requirement

        /**
         * SHA-256 of gemma3-270m-it-q8.litertlm (INT8 QAT, ~290 MB).
         * Source: litert-community/gemma-3-270m-it on Hugging Face.
         * Computed from downloaded file. Update when model version changes.
         */
        const val GEMMA_270M_V1_SHA256 =
            "757e9119fa5bd667a2774fb470ac4afcd3190a21c677f8e69a5d6bc908abdd63"

        /**
         * Direct download URL for the q8 LiteRT model.
         * Requires HuggingFace account + Gemma license acceptance.
         */
        const val GEMMA_270M_V1_URL =
            "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.litertlm"
    }
}
