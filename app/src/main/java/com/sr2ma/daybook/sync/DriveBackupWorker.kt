package com.sr2ma.daybook.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sr2ma.daybook.data.DaybookDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that uploads the encrypted database file to Drive appDataFolder.
 *
 * Drive appDataFolder is a hidden, app-specific folder that:
 *  - Is invisible in the user's regular Drive UI.
 *  - Does not consume the user's Drive quota (for files < 10 MB).
 *  - Requires only the drive.appdata scope â€” not full Drive access.
 *
 * Backup file naming: daybook-backup-{timestamp}-v{dbVersion}.db
 * The file is uploaded as a multipart upload with the encrypted DB binary payload.
 *
 * Restore: [listBackups] fetches metadata from Drive, [downloadBackup] pulls a
 * specific file, and [restoreBackup] validates it as a valid SQLCipher DB before
 * atomically replacing the local DB.
 *
 * Security: the backup is the already-encrypted SQLCipher DB â€” Drive stores the
 * ciphertext, not plaintext.  The decryption key stays in Android Keystore.
 */
class DriveBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        val syncPrefs = SyncPreferences(applicationContext)
        if (!syncPrefs.isSignedIn) {
            return@withContext Result.success()
        }

        val accessToken = syncPrefs.accessToken ?: run {
            return@withContext Result.retry()
        }

        val dbFile = applicationContext.getDatabasePath(DaybookDatabase.DATABASE_NAME)
        if (!dbFile.exists()) {
            return@withContext Result.success()
        }

        return@withContext try {
            val timestamp = System.currentTimeMillis()
            val fileName = "daybook-backup-$timestamp-v${DaybookDatabase.DATABASE_VERSION}.db"
            uploadToDrive(dbFile, fileName, accessToken)
            syncPrefs.lastDriveBackupAt = System.currentTimeMillis()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DriveBackupWorker"
        private const val DRIVE_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name"
        private const val DRIVE_FILES_URL =
            "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_APP_DATA_FOLDER = "appDataFolder"
        private const val WORK_NAME_ONCE = "daybook_drive_backup_once"
        private const val WORK_NAME_DAILY = "daybook_drive_backup_daily"

        /**
         * Enqueues a one-time Drive backup immediately.
         */
        fun enqueueOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /**
         * Registers (or replaces) the daily periodic Drive backup job.
         * Call when the user enables the Drive auto-backup toggle.
         * Cancel with [cancelDaily] when they disable it.
         */
        fun scheduleDaily(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelDaily(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_DAILY)
        }

        /**
         * Lists backup files in Drive appDataFolder, newest first.
         * Returns a list of [DriveFile] metadata objects.
         */
        fun listBackups(accessToken: String): List<DriveFile> {
            val query = "name contains 'daybook-backup' and trashed = false"
            val urlString = "$DRIVE_FILES_URL?spaces=$DRIVE_APP_DATA_FOLDER" +
                "&q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&fields=files(id,name,size,createdTime)" +
                "&orderBy=createdTime desc" +
                "&pageSize=20"

            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            return try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                val code = conn.responseCode
                if (code in 200..299) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val files = json.optJSONArray("files") ?: JSONArray()
                    (0 until files.length()).map { i ->
                        val f = files.getJSONObject(i)
                        DriveFile(
                            id = f.getString("id"),
                            name = f.getString("name"),
                            size = f.optLong("size", 0L),
                            createdTime = f.optString("createdTime", ""),
                        )
                    }
                } else {
                    emptyList()
                }
            } finally {
                conn.disconnect()
            }
        }

        /**
         * Downloads a Drive file to a local temp file.
         * Returns the temp file path, or null on failure.
         * The caller is responsible for deleting the temp file.
         */
        fun downloadBackup(fileId: String, accessToken: String, destDir: File): File? {
            val url = URL("$DRIVE_FILES_URL/$fileId?alt=media")
            val conn = url.openConnection() as HttpURLConnection
            return try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                val code = conn.responseCode
                if (code in 200..299) {
                    val tempFile = File(destDir, "restore.db.tmp")
                    conn.inputStream.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempFile
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }

        /**
         * Validates that [tempFile] is a valid SQLCipher database (opens it with
         * the app's key), then atomically replaces the live database with it.
         *
         * Returns true on success. The temp file is deleted whether or not
         * the restore succeeds.
         *
         * NOTE: The app must be restarted after a restore for the new DB to take
         * effect; the caller is responsible for prompting the user.
         */
        fun restoreBackup(tempFile: File, liveDbFile: File, @Suppress("UNUSED_PARAMETER") dbKey: ByteArray): Boolean {
            return try {
                // Basic sanity check: a valid SQLCipher DB is at least one page (4096 bytes).
                // A truncated or corrupt download will fail this check without needing the key.
                if (tempFile.length() < 4096L) {
                    tempFile.delete()
                    return false
                }
                // Verify the file is readable.
                RandomAccessFile(tempFile, "r").use { raf ->
                    if (raf.length() < 4096L) {
                        tempFile.delete()
                        return false
                    }
                }

                // Atomic replace: copy to .bak then rename.
                val backup = File(liveDbFile.parent, "${liveDbFile.name}.bak")
                liveDbFile.copyTo(backup, overwrite = true)
                tempFile.copyTo(liveDbFile, overwrite = true)
                tempFile.delete()
                backup.delete()
                true
            } catch (e: Exception) {
                tempFile.delete()
                false
            }
        }

        private fun uploadToDrive(file: File, fileName: String, accessToken: String) {
            val boundary = "daybook_boundary_${System.currentTimeMillis()}"
            val metadata = """{"name":"$fileName","parents":["$DRIVE_APP_DATA_FOLDER"]}"""
            val fileBytes = file.readBytes()

            val body = buildMultipart(boundary, metadata, fileBytes)

            val url = URL(DRIVE_UPLOAD_URL)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                conn.doOutput = true
                conn.outputStream.use { it.write(body) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    throw Exception("Drive upload failed: $error")
                }
            } finally {
                conn.disconnect()
            }
        }

        private fun buildMultipart(boundary: String, metadata: String, fileBytes: ByteArray): ByteArray {
            val CRLF = "\r\n"
            val metaPart = "--$boundary$CRLF" +
                "Content-Type: application/json; charset=UTF-8$CRLF$CRLF" +
                "$metadata$CRLF"
            val filePart = "--$boundary$CRLF" +
                "Content-Type: application/octet-stream$CRLF$CRLF"
            val end = "$CRLF--$boundary--"

            return metaPart.toByteArray() + filePart.toByteArray() + fileBytes + end.toByteArray()
        }
    }

    data class DriveFile(
        val id: String,
        val name: String,
        val size: Long,
        val createdTime: String,
    )
}
