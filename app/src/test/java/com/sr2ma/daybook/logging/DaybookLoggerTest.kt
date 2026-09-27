package com.sr2ma.daybook.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DaybookLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testFilesDir: File

    @Before
    fun setUp() {
        testFilesDir = tempFolder.newFolder("daybook_logging_test")
    }

    @Test
    fun `sanitize redacts Hugging Face API tokens`() {
        val input = "Downloading model using token hf_AbCdEfGhIjKlMnOpQrStUvWxYz1234567890"
        val sanitized = DaybookLogger.sanitize(input)

        assertFalse("Hugging Face token must not remain in sanitized string", sanitized.contains("AbCdEfGhIjKlMnOpQrStUvWxYz1234567890"))
        assertTrue("Sanitized string must contain redaction notice", sanitized.contains("hf_••••[REDACTED]"))
    }

    @Test
    fun `sanitize redacts Bearer authorization headers`() {
        val input = "Sending HTTP request with header: Bearer ya29.a0AfH6SMD_xyz123456789"
        val sanitized = DaybookLogger.sanitize(input)

        assertFalse("Bearer token must not remain in sanitized string", sanitized.contains("ya29.a0AfH6SMD_xyz123456789"))
        assertTrue("Sanitized string must contain Bearer [REDACTED]", sanitized.contains("Bearer [REDACTED]"))
    }

    @Test
    fun `sanitize redacts passwords and secrets`() {
        val input = "Configuring sync: password='MySuperSecretPassword99'"
        val sanitized = DaybookLogger.sanitize(input)

        assertFalse("Password must not be in sanitized string", sanitized.contains("MySuperSecretPassword99"))
        assertTrue("Sanitized string must contain redacted password", sanitized.contains("password=[REDACTED]"))
    }

    @Test
    fun `sanitize preserves normal application logs`() {
        val input = "Task 42 created with title 'Buy milk' and status OPEN"
        val sanitized = DaybookLogger.sanitize(input)

        assertEquals(input, sanitized)
    }

    @Test
    fun `logToFile writes formatted log lines to disk`() {
        DaybookLogger.logToFile(testFilesDir, "INFO", "SyncEngine", "Starting background sync")

        val logFile = DaybookLogger.getLogFile(testFilesDir)
        assertTrue(logFile.exists())
        val lines = logFile.readLines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("[INFO]"))
        assertTrue(lines[0].contains("[SyncEngine]"))
        assertTrue(lines[0].contains("Starting background sync"))
    }

    @Test
    fun `readRecentLogs retrieves recent lines`() {
        for (i in 1..10) {
            DaybookLogger.logToFile(testFilesDir, "DEBUG", "App", "Log line $i")
        }

        val recent = DaybookLogger.readRecentLogs(testFilesDir, maxLines = 5)
        assertEquals(5, recent.size)
        assertTrue(recent.last().contains("Log line 10"))
        assertTrue(recent.first().contains("Log line 6"))
    }

    @Test
    fun `logToFile rotates log when size threshold is reached`() {
        val smallCap = 200L // 200 bytes cap to test rotation easily

        // Write several lines until rotation occurs
        for (i in 1..15) {
            DaybookLogger.logToFile(testFilesDir, "WARN", "Storage", "Line $i for rotation testing", maxSizeBytes = smallCap)
        }

        val logsDir = DaybookLogger.getLogsDir(testFilesDir)
        val rotatedLog = File(logsDir, "daybook-app.log.1")
        val currentLog = File(logsDir, "daybook-app.log")

        assertTrue("Rotated log file must exist after exceeding threshold", rotatedLog.exists())
        assertTrue("Current log file must exist", currentLog.exists())
    }

    @Test
    fun `clearLogs removes log files`() {
        DaybookLogger.logToFile(testFilesDir, "ERROR", "Crash", "Fatal error occurred")
        assertTrue(DaybookLogger.getLogFile(testFilesDir).exists())

        DaybookLogger.clearLogs(testFilesDir)
        assertFalse(DaybookLogger.getLogFile(testFilesDir).exists())
    }
}
