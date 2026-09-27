package com.sr2ma.daybook.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import com.sr2ma.daybook.sync.SyncPreferences

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
     * @param authToken      Optional Hugging Face Bearer token for gated model access.
     * @param onProgress     Optional progress callback (called on IO thread).
     */
    suspend fun download(
        url: String,
        expectedSha256: String,
        versionTag: String = "v1",
        authToken: String? = null,
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

        // ── 4. Download → tmp (following redirects up to 8 hops) ─────────────
        try {
            var currentUrl = url
            var connection: HttpURLConnection? = null
            var redirects = 0
            val maxRedirects = 8
            var total = -1L

            // Record network access for offline privacy tracking
            SyncPreferences(context).recordNetworkAccess()

            while (true) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "Daybook-ModelDownloader/1.0")
                    val parsedUrl = URL(currentUrl)
                    val isHfHost = parsedUrl.host.equals("huggingface.co", ignoreCase = true) ||
                        (parsedUrl.host.endsWith(".huggingface.co", ignoreCase = true) && !parsedUrl.host.startsWith("cdn-lfs."))
                    val hasPresignedAuth = parsedUrl.query?.contains("X-Amz-Signature") == true
                    if (!authToken.isNullOrBlank() && isHfHost && !hasPresignedAuth) {
                        setRequestProperty("Authorization", "Bearer ${authToken.trim()}")
                    }
                }
                val code = conn.responseCode
                if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER, 307, 308)) {
                    val location = conn.getHeaderField("Location")
                        ?: return@withContext DownloadResult.Failure("Redirected without Location header")
                    currentUrl = if (location.startsWith("http")) location else URL(URL(currentUrl), location).toString()
                    conn.disconnect()
                    redirects++
                    if (redirects > maxRedirects) {
                        return@withContext DownloadResult.Failure("Too many redirects ($redirects)")
                    }
                    continue
                }
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    conn.disconnect()
                    return@withContext DownloadResult.Failure(
                        "Server returned HTTP 401 Unauthorized: Hugging Face gated model requires authorization. Please configure your Hugging Face API token in Settings or use local model import.",
                    )
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@withContext DownloadResult.Failure("Server returned HTTP $code")
                }
                connection = conn
                total = conn.contentLengthLong
                break
            }

            connection!!.inputStream.use { input ->
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
            connection.disconnect()
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

    /**
     * Imports a pre-downloaded model file from an [InputStream] (e.g. from Storage Access Framework / file picker).
     *
     * Validates free space, copies into `.tmp`, verifies hash, and atomically renames.
     */
    suspend fun importModel(
        inputStream: java.io.InputStream,
        expectedSha256: String = GEMMA_270M_V1_SHA256,
        versionTag: String = "v1",
        onProgress: ProgressListener? = null,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val modelDir = context.filesDir
        val finalFile = File(modelDir, modelFileName(versionTag))
        val tmpFile = File(modelDir, "${modelFileName(versionTag)}.tmp")

        val freeBytes = modelDir.freeSpace
        if (freeBytes < MIN_FREE_BYTES) {
            return@withContext DownloadResult.Failure(
                "Not enough storage: need at least 250 MB free, only ${freeBytes / MB}MB available."
            )
        }

        tmpFile.delete()

        try {
            inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        copied += read
                        onProgress?.onProgress(copied, -1L)
                    }
                }
            }
        } catch (e: Exception) {
            tmpFile.delete()
            return@withContext DownloadResult.Failure("Import failed: ${e.message}")
        }

        // SHA-256 verification
        val actualHash = sha256(tmpFile)
        if (actualHash != expectedSha256.lowercase()) {
            tmpFile.delete()
            return@withContext DownloadResult.Failure(
                "SHA-256 mismatch — file is corrupted or not a valid Gemma 3 270M model."
            )
        }

        if (!tmpFile.renameTo(finalFile)) {
            tmpFile.delete()
            return@withContext DownloadResult.Failure("Could not rename model file after import.")
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
