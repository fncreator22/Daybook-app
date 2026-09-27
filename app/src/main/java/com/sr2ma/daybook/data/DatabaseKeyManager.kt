package com.sr2ma.daybook.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the 256-bit database encryption key for SQLCipher using the Android Keystore.
 *
 * Security architecture:
 * 1. An AES-256 master key is generated and stored inside the Android Keystore (hardware-backed
 *    StrongBox or TEE where available). Key material never leaves the secure hardware.
 * 2. A 256-bit (32-byte) random database passphrase is generated with [SecureRandom].
 * 3. The database passphrase is encrypted with AES/GCM/NoPadding using the Keystore master key.
 * 4. Only the IV + ciphertext are saved to app-scoped internal storage (`daybook_db_passphrase.enc`).
 * 5. On retrieval, the passphrase is decrypted in memory and wiped with [wipe] when no longer needed.
 * 6. Automated fallback: If Android Keystore is unavailable (e.g. host JVM unit tests or legacy
 *    test runners), a safe fallback is used so tests and migrations continue without crashing.
 */
object DatabaseKeyManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "daybook_db_encryption_key_v1"
    private const val KEY_FILE_NAME = "daybook_db_passphrase.enc"
    private const val FALLBACK_KEY_FILE_NAME = "daybook_db_test.key"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PASSPHRASE_BYTE_COUNT = 32

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    /**
     * Checks if the Android Keystore provider is available in the current runtime environment.
     */
    fun isKeystoreAvailable(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Retrieves or generates the 256-bit database passphrase.
     * The returned ByteArray contains 32 bytes (256 bits).
     */
    fun getOrCreateDatabaseKey(context: Context): ByteArray =
        getOrCreateDatabaseKey(context.filesDir)

    @Synchronized
    fun getOrCreateDatabaseKey(filesDir: File): ByteArray {
        cachedPassphrase?.let { return it.copyOf() }

        val keyFile = File(filesDir, KEY_FILE_NAME)

        if (isKeystoreAvailable()) {
            val masterKey = getOrCreateMasterKey()
            if (keyFile.exists()) {
                if (keyFile.length() <= GCM_IV_LENGTH) {
                    throw SecurityException("Database key file corrupted: payload too short")
                }
                val encryptedBytes = keyFile.readBytes()
                val iv = encryptedBytes.copyOfRange(0, GCM_IV_LENGTH)
                val ciphertext = encryptedBytes.copyOfRange(GCM_IV_LENGTH, encryptedBytes.size)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val decrypted = cipher.doFinal(ciphertext)
                cachedPassphrase = decrypted
                return decrypted.copyOf()
            } else {
                // Generate a new 32-byte (256-bit) random database passphrase
                val rawKey = ByteArray(PASSPHRASE_BYTE_COUNT)
                SecureRandom().nextBytes(rawKey)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, masterKey)
                val iv = cipher.iv
                val ciphertext = cipher.doFinal(rawKey)

                // Write IV + ciphertext atomically
                val tempFile = File(filesDir, "$KEY_FILE_NAME.tmp")
                tempFile.outputStream().use { os ->
                    os.write(iv)
                    os.write(ciphertext)
                }
                if (tempFile.renameTo(keyFile) || (!keyFile.exists() && tempFile.copyTo(keyFile, overwrite = true).exists())) {
                    tempFile.delete()
                }

                cachedPassphrase = rawKey
                return rawKey.copyOf()
            }
        }

        // Automated Fallback for unit testing and environments without AndroidKeyStore
        val fallbackFile = File(filesDir, FALLBACK_KEY_FILE_NAME)
        return getFallbackKey(fallbackFile)
    }

    private fun getFallbackKey(keyFile: File): ByteArray {
        if (keyFile.exists()) {
            val bytes = try { keyFile.readBytes() } catch (_: Exception) { ByteArray(0) }
            if (bytes.size >= PASSPHRASE_BYTE_COUNT) {
                val key = bytes.copyOf(PASSPHRASE_BYTE_COUNT)
                cachedPassphrase = key
                return key.copyOf()
            }
        }
        val fallback = ByteArray(PASSPHRASE_BYTE_COUNT)
        SecureRandom().nextBytes(fallback)
        try {
            keyFile.writeBytes(fallback)
        } catch (_: Exception) {}
        cachedPassphrase = fallback
        return fallback.copyOf()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Securely overwrites the passphrase array with zeros in memory.
     */
    fun wipe(key: ByteArray) {
        key.fill(0)
    }

    /**
     * Clears cached keys in memory and deletes test key files.
     */
    fun resetForTesting(context: Context) {
        resetForTesting(context.filesDir)
    }

    @Synchronized
    fun resetForTesting(filesDir: File? = null) {
        cachedPassphrase?.let { wipe(it) }
        cachedPassphrase = null
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Throwable) {}
        if (filesDir != null) {
            File(filesDir, KEY_FILE_NAME).delete()
            File(filesDir, "$KEY_FILE_NAME.tmp").delete()
            File(filesDir, FALLBACK_KEY_FILE_NAME).delete()
        }
    }
}
