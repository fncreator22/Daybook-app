package com.sr2ma.daybook.sync

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncPreferencesTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var syncPrefs: SyncPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        syncPrefs = SyncPreferences(customPrefs = fakePrefs)
    }

    @Test
    fun `profile first name, last name, and phone number persist correctly`() {
        assertNull(syncPrefs.profileFirstName)
        assertNull(syncPrefs.profileLastName)
        assertNull(syncPrefs.profilePhoneNumber)

        syncPrefs.profileFirstName = "Alice"
        syncPrefs.profileLastName = "Smith"
        syncPrefs.profilePhoneNumber = "+1-555-0199"

        assertEquals("Alice", syncPrefs.profileFirstName)
        assertEquals("Smith", syncPrefs.profileLastName)
        assertEquals("+1-555-0199", syncPrefs.profilePhoneNumber)
    }

    @Test
    fun `sub-emails can be added, normalized to lowercase, and removed`() {
        syncPrefs.addSubEmail(" WORK@Company.Com ")
        syncPrefs.addSubEmail("personal@gmail.com")

        val emails = syncPrefs.profileSubEmails
        assertEquals(2, emails.size)
        assertTrue(emails.contains("work@company.com"))
        assertTrue(emails.contains("personal@gmail.com"))

        syncPrefs.removeSubEmail("work@company.com")
        assertFalse(syncPrefs.profileSubEmails.contains("work@company.com"))
        assertTrue(syncPrefs.profileSubEmails.contains("personal@gmail.com"))
    }

    @Test
    fun `generateOtp produces 6-digit code and verifyOtp validates it successfully`() {
        val email = "user@example.com"
        val otp = syncPrefs.generateOtp(email)

        assertEquals(6, otp.length)
        assertTrue(otp.all { it.isDigit() })
        assertFalse(syncPrefs.isEmailVerified(email))

        val verified = syncPrefs.verifyOtp(email, otp)
        assertTrue(verified)
        assertTrue(syncPrefs.isEmailVerified(email))
        assertTrue(syncPrefs.verifiedEmails.contains(email))

        // OTP should be consumed / removed after successful verification
        val reVerify = syncPrefs.verifyOtp(email, otp)
        assertFalse(reVerify)
    }

    @Test
    fun `verifyOtp rejects incorrect code`() {
        val email = "user@example.com"
        syncPrefs.generateOtp(email)

        val success = syncPrefs.verifyOtp(email, "000000")
        assertFalse(success)
        assertFalse(syncPrefs.isEmailVerified(email))
    }

    @Test
    fun `primary email verification generates offline access token`() {
        val email = "lead@example.com"
        syncPrefs.profilePrimaryEmail = email
        assertNull(syncPrefs.accessToken)

        val otp = syncPrefs.generateOtp(email)
        val success = syncPrefs.verifyOtp(email, otp)

        assertTrue(success)
        assertNotNull(syncPrefs.accessToken)
        assertTrue(syncPrefs.accessToken!!.startsWith("offline_verified_"))
        assertEquals(Long.MAX_VALUE, syncPrefs.tokenExpiry)
    }

    @Test
    fun `global autonomy guardrail defaults to ALWAYS_ASK and persists updates`() {
        assertEquals("ALWAYS_ASK", syncPrefs.globalAutonomyGuardrail)

        syncPrefs.globalAutonomyGuardrail = "HYBRID"
        assertEquals("HYBRID", syncPrefs.globalAutonomyGuardrail)

        syncPrefs.globalAutonomyGuardrail = "FULL_AUTONOMY"
        assertEquals("FULL_AUTONOMY", syncPrefs.globalAutonomyGuardrail)
    }

    @Test
    fun `recordNetworkAccess updates last network access timestamp`() {
        assertEquals(0L, syncPrefs.lastNetworkAccessAt)

        val before = System.currentTimeMillis()
        syncPrefs.recordNetworkAccess()
        val after = System.currentTimeMillis()

        assertTrue(syncPrefs.lastNetworkAccessAt in before..after)
    }

    @Test
    fun `demoDataEnabled defaults to false and persists updates`() {
        assertFalse(syncPrefs.demoDataEnabled)

        syncPrefs.demoDataEnabled = true
        assertTrue(syncPrefs.demoDataEnabled)

        syncPrefs.demoDataEnabled = false
        assertFalse(syncPrefs.demoDataEnabled)
    }
}

/**
 * In-memory FakeSharedPreferences for fast JVM unit tests without Android runtime dependencies.
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data

    override fun getString(key: String?, defValue: String?): String? =
        data[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (data[key] as? Number)?.toInt() ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (data[key] as? Number)?.toLong() ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (data[key] as? Number)?.toFloat() ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        data[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val staged = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) staged[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) staged[key] = values?.toSet()
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) staged[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) staged[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) staged[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) staged[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removed.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) storage.clear()
            removed.forEach { storage.remove(it) }
            staged.forEach { (k, v) ->
                if (v == null) storage.remove(k) else storage[k] = v
            }
        }
    }
}
