package com.sr2ma.daybook

import android.app.Application
import android.content.Context
import com.sr2ma.daybook.ai.NightlyAgentWorker
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.DaybookRepository

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
