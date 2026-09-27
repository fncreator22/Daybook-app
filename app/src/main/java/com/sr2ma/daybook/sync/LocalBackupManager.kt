package com.sr2ma.daybook.sync

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sr2ma.daybook.data.DatabaseKeyManager
import com.sr2ma.daybook.data.DaybookDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Storage Access Framework (SAF) Encrypted Local Backup Manager.
 *
 * Implements hardware-grade encrypted export and import for the Daybook database:
 * - Uses AES-256-GCM (AEAD) with 128-bit authentication tag for authenticated encryption.
 * - Tamper detection: any altered byte or incorrect key fails immediately with [GeneralSecurityException].
 * - Supports both device Keystore key and optional user-specified master passphrases via PBKDF2WithHmacSHA256.
 * - Safely stages restored databases to prevent partial writes or corrupt states.
 * - Generates SAF Intents for `ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT`.
 */
class LocalBackupManager(
    private val context: Context? = null,
    private val targetDbFile: File = context?.getDatabasePath(DaybookDatabase.DATABASE_NAME) ?: File("daybook.db"),
    private val workingDir: File = context?.filesDir ?: File("."),
    private val walFlusher: (() -> Unit)? = null,
    private val dbCloser: (() -> Unit)? = null,
) {

    // ── SAF Intent Helpers ───────────────────────────────────────────────────

    /**
     * Creates an [Intent] for `ACTION_CREATE_DOCUMENT` targeting encrypted backup files.
     */
    fun createExportIntent(suggestedName: String = defaultBackupFileName()): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }
    }

    /**
     * Creates an [Intent] for `ACTION_OPEN_DOCUMENT` to pick an encrypted backup file.
     */
    fun createImportIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_TYPE, "application/octet-stream", "*/*"))
        }
    }

    // ── Export ───────────────────────────────────────────────────────────────

    /**
     * Exports the encrypted database to an [OutputStream] provided by SAF.
     *
     * @param outputStream Destination stream (e.g. from ContentResolver.openOutputStream(uri)).
     * @param userPassphrase Optional user password. If null or blank, the Keystore DB key is used.
     * @return Number of encrypted bytes written.
     */
    suspend fun exportEncryptedDatabase(
        outputStream: OutputStream,
        userPassphrase: String? = null,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            if (!targetDbFile.exists()) {
                throw IllegalStateException("Database file does not exist at ${targetDbFile.absolutePath}")
            }

            // Flush database write-ahead log if SQLite database is currently open
            walFlusher?.invoke() ?: flushDatabaseWal()

            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }
            val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { random.nextBytes(it) }

            val secretKey = deriveKey(userPassphrase, salt)

            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            var totalBytesWritten = 0L

            outputStream.use { out ->
                // 1. Magic Header (8 bytes)
                out.write(MAGIC_HEADER)
                totalBytesWritten += MAGIC_HEADER.size

                // 2. Salt (16 bytes)
                out.write(salt)
                totalBytesWritten += salt.size

                // 3. IV (12 bytes)
                out.write(iv)
                totalBytesWritten += iv.size

                // 4. Encrypted Database Stream
                val cipherOut = CipherOutputStream(out, cipher)
                FileInputStream(targetDbFile).use { fileIn ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (fileIn.read(buffer).also { read = it } != -1) {
                        cipherOut.write(buffer, 0, read)
                        totalBytesWritten += read
                    }
                }
                cipherOut.flush()
                cipherOut.close()
            }

            totalBytesWritten
        }
    }

    /**
     * Convenience method to export directly to a SAF document Uri.
     */
    suspend fun exportToUri(
        uri: Uri,
        contentResolver: ContentResolver,
        userPassphrase: String? = null,
    ): Result<Long> {
        val stream = contentResolver.openOutputStream(uri, "wt")
            ?: return Result.failure(IllegalStateException("Cannot open output stream for Uri $uri"))
        return exportEncryptedDatabase(stream, userPassphrase)
    }

    // ── Import ───────────────────────────────────────────────────────────────

    /**
     * Imports an encrypted database backup from an [InputStream] provided by SAF.
     *
     * Validates magic header, decrypts into a staging file, verifies integrity,
     * and atomically replaces the live database.
     */
    suspend fun importEncryptedDatabase(
        inputStream: InputStream,
        userPassphrase: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val stagingFile = File(workingDir, "daybook_restore.tmp")
        val backupFile = File(workingDir, "daybook_rollback.bak")

        runCatching {
            inputStream.use { inStream ->
                // 1. Verify Magic Header
                val header = ByteArray(MAGIC_HEADER.size)
                val readHeader = inStream.read(header)
                if (readHeader != MAGIC_HEADER.size || !header.contentEquals(MAGIC_HEADER)) {
                    throw IllegalArgumentException("Not a valid Daybook encrypted backup file (magic header mismatch)")
                }

                // 2. Read Salt
                val salt = ByteArray(SALT_LENGTH_BYTES)
                if (inStream.read(salt) != SALT_LENGTH_BYTES) {
                    throw IllegalArgumentException("Corrupt backup file: truncated salt")
                }

                // 3. Read IV
                val iv = ByteArray(GCM_IV_LENGTH_BYTES)
                if (inStream.read(iv) != GCM_IV_LENGTH_BYTES) {
                    throw IllegalArgumentException("Corrupt backup file: truncated IV")
                }

                val secretKey = deriveKey(userPassphrase, salt)

                val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

                // 4. Decrypt to temporary staging file using update + doFinal.
                // This guarantees AEADBadTagException is raised on invalid tag,
                // avoiding buggy CipherInputStream implementations in certain runtimes.
                stagingFile.delete()
                FileOutputStream(stagingFile).use { fileOut ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        val decrypted = cipher.update(buffer, 0, read)
                        if (decrypted != null && decrypted.isNotEmpty()) {
                            fileOut.write(decrypted)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        fileOut.write(finalBytes)
                    }
                }
            }

            // 5. Verify decrypted database file format
            if (!isValidDatabaseFile(stagingFile)) {
                stagingFile.delete()
                throw IllegalArgumentException("Decrypted file is not a valid SQLite or SQLCipher database")
            }

            // 6. Close database singleton before file swap
            dbCloser?.invoke() ?: runCatching { context?.let { DaybookDatabase.getInstance(it).close() } }

            // 7. Atomic database file swap with rollback safety
            targetDbFile.parentFile?.mkdirs()
            backupFile.delete()

            if (targetDbFile.exists()) {
                if (!targetDbFile.renameTo(backupFile)) {
                    stagingFile.delete()
                    throw IllegalStateException("Failed to move active database to rollback location")
                }
            }

            if (!stagingFile.renameTo(targetDbFile)) {
                // Rollback
                if (backupFile.exists()) {
                    backupFile.renameTo(targetDbFile)
                }
                stagingFile.delete()
                throw IllegalStateException("Failed to install restored database file")
            }

            // Clean up auxiliary WAL/SHM files
            File(targetDbFile.parentFile, "${DaybookDatabase.DATABASE_NAME}-wal").delete()
            File(targetDbFile.parentFile, "${DaybookDatabase.DATABASE_NAME}-shm").delete()
            File(targetDbFile.parentFile, "${DaybookDatabase.DATABASE_NAME}-journal").delete()
            backupFile.delete()

            Unit
        }.onFailure {
            stagingFile.delete()
        }
    }

    /**
     * Convenience method to import directly from a SAF document Uri.
     */
    suspend fun importFromUri(
        uri: Uri,
        contentResolver: ContentResolver,
        userPassphrase: String? = null,
    ): Result<Unit> {
        val stream = contentResolver.openInputStream(uri)
            ?: return Result.failure(IllegalStateException("Cannot open input stream for Uri $uri"))
        return importEncryptedDatabase(stream, userPassphrase)
    }

    // ── Helper functions ──────────────────────────────────────────────────────

    private fun deriveKey(userPassphrase: String?, salt: ByteArray): SecretKey {
        return if (!userPassphrase.isNullOrBlank()) {
            val spec = PBEKeySpec(userPassphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } else {
            val dbKey = if (context != null) {
                DatabaseKeyManager.getOrCreateDatabaseKey(context)
            } else {
                DatabaseKeyManager.getOrCreateDatabaseKey(workingDir)
            }
            SecretKeySpec(dbKey, "AES")
        }
    }

    private fun flushDatabaseWal() {
        try {
            val ctx = context ?: return
            val db = DaybookDatabase.getInstance(ctx).readableDatabase
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                cursor.moveToFirst()
            }
        } catch (_: Exception) {
        }
    }

    private fun isValidDatabaseFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(16)
        return try {
            FileInputStream(file).use { it.read(header) }
            val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            // Either a standard plaintext SQLite database or an encrypted SQLCipher database
            // (SQLCipher databases have a 16-byte salt header and are multiples of page size >= 512).
            if (header.contentEquals(sqliteHeader)) {
                true
            } else {
                file.length() >= 512 && file.length() % 512 == 0L
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val MIME_TYPE = "application/x-daybook-backup"
        val MAGIC_HEADER = "DBENC01\u0000".toByteArray(Charsets.US_ASCII)
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val SALT_LENGTH_BYTES = 16
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PBKDF2_ITERATIONS = 10_000
        private const val KEY_LENGTH_BITS = 256
        private const val BUFFER_SIZE = 8192

        fun defaultBackupFileName(date: LocalDate = LocalDate.now()): String {
            val formatted = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            return "daybook-encrypted-backup-$formatted.dbenc"
        }
    }
}
