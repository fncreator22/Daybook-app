package com.sr2ma.daybook

import android.app.Application
import android.content.Context
import com.sr2ma.daybook.ai.BriefingNotificationWorker
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

    // ── On-device AI (Phase 3) ─────────────────────────────────────────────────
    // Lazy-load: the LlmEngine and ModelDownloader are created only when a
    // conversation turn reaches an UNKNOWN intent. Never instantiated at startup.

    val modelDownloader: com.sr2ma.daybook.ai.ModelDownloader by lazy {
        com.sr2ma.daybook.ai.ModelDownloader(applicationContext)
    }

    /**
     * Singleton [LlmEngine] for the process lifetime.
     *
     * AGENTS.md non-negotiables enforced inside [LlmEngine]:
     *  - Lazy-load: not created until first [LlmEngine.infer] call.
     *  - Idle release: engine closed after 3 min of no inference.
     *  - Single queue: one Mutex, calls queue, never run in parallel.
     *  - Hard timeout: 30 s [kotlinx.coroutines.withTimeout] per call.
     */
    val llmEngine: com.sr2ma.daybook.ai.LlmEngine by lazy {
        com.sr2ma.daybook.ai.LlmEngine(
            context      = applicationContext,
            downloader   = modelDownloader,
            versionTag   = "v1",
        )
    }
}

class DaybookApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Create notification channels before any worker fires (idempotent).
        BriefingNotificationWorker.createChannels(this)
        // Schedule nightly rule-engine + optional AI job.
        NightlyAgentWorker.schedule(this)
        // Schedule morning briefing at 07:30 every day.
        BriefingNotificationWorker.schedule(this)
    }
}
