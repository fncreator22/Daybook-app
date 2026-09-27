package com.sr2ma.daybook.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

class LocalBackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var workDir: File
    private lateinit var dbFile: File
    private lateinit var backupManager: LocalBackupManager
    private val testPassphrase = "TestSafePassword2026!"

    private val sampleDatabaseContent = (
        "SQLite format 3\u0000" +
        "Daybook sample database content with tasks, meetings, and passes"
    ).toByteArray(Charsets.US_ASCII)

    @Before
    fun setUp() {
        workDir = tempFolder.newFolder("work")
        dbFile = File(workDir, "test_daybook.db")
        dbFile.writeBytes(sampleDatabaseContent)

        backupManager = LocalBackupManager(
            context = null,
            targetDbFile = dbFile,
            workingDir = workDir,
            walFlusher = { /* no-op in tests */ },
            dbCloser = { /* no-op in tests */ },
        )
    }

    @Test
    fun `exportEncryptedDatabase produces valid magic header salt and iv`() = runBlocking {
        val out = ByteArrayOutputStream()
        val result = backupManager.exportEncryptedDatabase(out, testPassphrase)

        assertTrue(result.isSuccess)
        val exportedBytes = out.toByteArray()
        assertTrue("Exported bytes must be larger than header + salt + IV", exportedBytes.size > 36)

        // Magic Header (8 bytes)
        val header = exportedBytes.copyOfRange(0, 8)
        assertTrue("Magic header must match DBENC01", header.contentEquals(LocalBackupManager.MAGIC_HEADER))

        // Salt (16 bytes)
        val salt = exportedBytes.copyOfRange(8, 24)
        assertEquals(16, salt.size)
        assertFalse("Salt must not be all zeros", salt.all { it == 0.toByte() })

        // IV (12 bytes)
        val iv = exportedBytes.copyOfRange(24, 36)
        assertEquals(12, iv.size)
        assertFalse("IV must not be all zeros", iv.all { it == 0.toByte() })
    }

    @Test
    fun `roundtrip export and import with passphrase restores exact database file`() = runBlocking {
        val out = ByteArrayOutputStream()
        val exportResult = backupManager.exportEncryptedDatabase(out, testPassphrase)
        assertTrue(exportResult.isSuccess)

        // Modify the active dbFile so we can confirm import actually overwrites it
        dbFile.writeBytes("Modified active database before restore".toByteArray())

        val inStream = ByteArrayInputStream(out.toByteArray())
        val importResult = backupManager.importEncryptedDatabase(inStream, testPassphrase)
        assertTrue("Import must succeed with correct passphrase", importResult.isSuccess)

        val restoredContent = dbFile.readBytes()
        assertTrue("Restored content must match original database content", sampleDatabaseContent.contentEquals(restoredContent))
    }

    @Test
    fun `roundtrip export and import with default Keystore key restores exact database file`() = runBlocking {
        val out = ByteArrayOutputStream()
        val exportResult = backupManager.exportEncryptedDatabase(out, userPassphrase = null)
        assertTrue(exportResult.isSuccess)

        dbFile.writeBytes("Changed before restore".toByteArray())

        val inStream = ByteArrayInputStream(out.toByteArray())
        val importResult = backupManager.importEncryptedDatabase(inStream, userPassphrase = null)
        assertTrue("Import must succeed with Keystore key", importResult.isSuccess)

        val restoredContent = dbFile.readBytes()
        assertTrue(sampleDatabaseContent.contentEquals(restoredContent))
    }

    @Test
    fun `importEncryptedDatabase fails when ciphertext is tampered and does not replace target`() = runBlocking {
        val out = ByteArrayOutputStream()
        backupManager.exportEncryptedDatabase(out, testPassphrase)
        val bytes = out.toByteArray()

        // Tamper with one byte in the ciphertext payload (after 36 bytes of header, salt, iv)
        val tamperedIndex = 40
        bytes[tamperedIndex] = (bytes[tamperedIndex].toInt() xor 0xFF).toByte()

        val originalDbBytes = dbFile.readBytes()

        val inStream = ByteArrayInputStream(bytes)
        val importResult = backupManager.importEncryptedDatabase(inStream, testPassphrase)

        assertTrue("Import must fail on tampered ciphertext", importResult.isFailure)
        assertTrue("Active database file must remain untouched after failed restore", originalDbBytes.contentEquals(dbFile.readBytes()))
    }

    @Test
    fun `importEncryptedDatabase fails when wrong passphrase is provided and does not replace target`() = runBlocking {
        val out = ByteArrayOutputStream()
        backupManager.exportEncryptedDatabase(out, testPassphrase)

        val originalDbBytes = dbFile.readBytes()

        val inStream = ByteArrayInputStream(out.toByteArray())
        val importResult = backupManager.importEncryptedDatabase(inStream, "WrongPassword2026!")

        assertTrue("Import must fail with wrong passphrase", importResult.isFailure)
        assertTrue("Active database file must remain untouched", originalDbBytes.contentEquals(dbFile.readBytes()))
    }

    @Test
    fun `importEncryptedDatabase rejects invalid magic header`() = runBlocking {
        val invalidBytes = "CORRUPT0\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(50)
        val inStream = ByteArrayInputStream(invalidBytes)

        val importResult = backupManager.importEncryptedDatabase(inStream, testPassphrase)
        assertTrue(importResult.isFailure)
        assertTrue(importResult.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `importEncryptedDatabase rejects truncated input stream`() = runBlocking {
        val truncated = LocalBackupManager.MAGIC_HEADER.copyOf(10) // Only 10 bytes total
        val inStream = ByteArrayInputStream(truncated)

        val importResult = backupManager.importEncryptedDatabase(inStream, testPassphrase)
        assertTrue(importResult.isFailure)
    }

    @Test
    fun `defaultBackupFileName generates expected naming convention`() {
        val name = LocalBackupManager.defaultBackupFileName(LocalDate.of(2026, 9, 26))
        assertEquals("daybook-encrypted-backup-2026-09-26.dbenc", name)
    }
}
