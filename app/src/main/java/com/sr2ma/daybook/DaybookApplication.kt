package com.sr2ma.daybook

import android.app.Application
import android.content.Context
import com.sr2ma.daybook.ai.NightlyAgentWorker
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.DaybookRepository
import com.sr2ma.daybook.sync.GoogleAuthClient
import com.sr2ma.daybook.sync.SyncManager
import com.sr2ma.daybook.sync.SyncPreferences

/**
 * Holds the objects that live as long as the process.
 *
 * This is manual dependency injection: one database, one repository, created on
 * first use. Hilt would add an annotation processor and a plugin to buy nothing
 * at this size, and everything here is trivially replaceable in a test by
 * constructing the object under test with a different repository.
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    /** Opening the database touches the disk, so it waits until something asks. */
    val repository: DaybookRepository by lazy {
        DaybookRepository(DaybookDatabase(applicationContext))
    }

    // ── Sync & Backup (Phase 4) ────────────────────────────────────────────────
    // All lazy — zero cost until the user navigates to Settings and the sync
    // section is first composed.

    val syncPreferences: SyncPreferences by lazy { SyncPreferences(applicationContext) }

    /**
     * OAuth Web Client ID from Google Cloud Console.
     * Replace with the actual value once credentials are configured.
     * See INSTALL.md § "Google Sign-In setup".
     */
    private val googleServerClientId: String by lazy {
        applicationContext.getString(R.string.google_server_client_id)
    }

    val googleAuthClient: GoogleAuthClient by lazy {
        GoogleAuthClient(applicationContext, syncPreferences, googleServerClientId)
    }

    val syncManager: SyncManager by lazy {
        SyncManager(applicationContext, syncPreferences, googleAuthClient)
    }
}

class DaybookApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Schedule the nightly rule-engine + optional AI job.
        // Idempotent: WorkManager deduplicates using KEEP policy.
        NightlyAgentWorker.schedule(this)
    }
}
