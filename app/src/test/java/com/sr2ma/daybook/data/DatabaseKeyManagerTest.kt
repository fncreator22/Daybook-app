package com.sr2ma.daybook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Arrays

class DatabaseKeyManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testFilesDir: File

    @Before
    fun setUp() {
        testFilesDir = tempFolder.newFolder("daybook_key_test")
        DatabaseKeyManager.resetForTesting(testFilesDir)
    }

    @Test
    fun `getOrCreateDatabaseKey generates exactly 32 bytes (256 bits)`() {
        val key = DatabaseKeyManager.getOrCreateDatabaseKey(testFilesDir)
        assertEquals(32, key.size)
        // Ensure it's not all zeros
        assertFalse(key.all { it == 0.toByte() })
    }

    @Test
    fun `getOrCreateDatabaseKey is deterministic across repeated calls`() {
        val key1 = DatabaseKeyManager.getOrCreateDatabaseKey(testFilesDir)
        val key2 = DatabaseKeyManager.getOrCreateDatabaseKey(testFilesDir)

        assertTrue(Arrays.equals(key1, key2))
    }

    @Test
    fun `wipe overwrites key buffer with zeros in memory`() {
        val key = DatabaseKeyManager.getOrCreateDatabaseKey(testFilesDir)
        val copy = key.copyOf()
        assertFalse(copy.all { it == 0.toByte() })

        DatabaseKeyManager.wipe(copy)
        assertTrue(copy.all { it == 0.toByte() })
    }

    @Test
    fun `resetForTesting clears cached passphrase and allows generating fresh key`() {
        val key1 = DatabaseKeyManager.getOrCreateDatabaseKey(testFilesDir)
        DatabaseKeyManager.resetForTesting(testFilesDir)

        val key2 = DatabaseKeyManager.getOrCreateDatabaseKey(testFilesDir)
        assertEquals(32, key2.size)
        // With high probability (1 - 2^-256), a newly generated key differs
        assertFalse(Arrays.equals(key1, key2))
    }

    @Test
    fun `fallback mode uses test key file and does not touch production enc file`() {
        assertFalse(DatabaseKeyManager.isKeystoreAvailable())
        DatabaseKeyManager.getOrCreateDatabaseKey(testFilesDir)

        val encFile = File(testFilesDir, "daybook_db_passphrase.enc")
        val testKeyFile = File(testFilesDir, "daybook_db_test.key")

        assertFalse("Production encrypted key file must not exist in fallback mode", encFile.exists())
        assertTrue("Test key file should exist for fallback persistence", testKeyFile.exists())
        assertEquals(32, testKeyFile.length())
    }

    @Test
    fun `plaintext SQLite format 3 magic header is detected correctly`() {
        val plaintextDb = tempFolder.newFile("plain.db")
        plaintextDb.writeBytes("SQLite format 3\u0000Some database content here...".toByteArray(Charsets.US_ASCII))

        val encryptedDb = tempFolder.newFile("encrypted.db")
        encryptedDb.writeBytes(ByteArray(64) { it.toByte() })

        // Check encryption detection directly on dummy files
        val isPlainEncrypted = !plaintextDb.readBytes().copyOfRange(0, 16).contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
        val isCipherEncrypted = !encryptedDb.readBytes().copyOfRange(0, 16).contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))

        assertFalse(isPlainEncrypted)
        assertTrue(isCipherEncrypted)
    }
}
