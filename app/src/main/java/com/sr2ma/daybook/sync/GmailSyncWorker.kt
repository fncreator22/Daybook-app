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
import com.sr2ma.daybook.data.dao.GmailDao
import com.sr2ma.daybook.logging.DaybookLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically checks Gmail messages and extracts actionable items.
 *
 * Designed for maximum battery efficiency:
 * - Only runs when connected to network AND battery is not low.
 * - Minimum 15-minute periodic interval via WorkManager.
 * - HTTP connections are disconnected immediately upon completion.
 * - Logs sync diffs and updates [SyncPreferences].
 */
class GmailSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val syncPrefs = SyncPreferences(applicationContext)

        if (!syncPrefs.isSignedIn || !syncPrefs.gmailSync) {
            return@withContext Result.success()
        }

        val db = DaybookDatabase.getInstance(applicationContext)
        val dao = GmailDao(db)
        val engine = GmailSyncEngine(dao, syncPrefs)

        DaybookLogger.i(applicationContext, TAG, "Starting periodic Gmail sync pass")
        val result = engine.sync()

        when (result) {
            is GmailSyncEngine.SyncResult.Success -> {
                val diff = "Fetched: ${result.fetchedCount} | Actions: ${result.actionableCount}"
                syncPrefs.lastGmailSyncDiffSummary = diff
                DaybookLogger.i(
                    applicationContext,
                    TAG,
                    "Periodic sync succeeded. $diff",
                )
                Result.success()
            }
            is GmailSyncEngine.SyncResult.Error -> {
                DaybookLogger.w(
                    applicationContext,
                    TAG,
                    "Periodic sync encountered error: ${result.message}",
                )
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "GmailSyncWorker"
        const val WORK_NAME_PERIODIC = "daybook_gmail_sync_periodic"
        const val WORK_NAME_ONCE = "daybook_gmail_sync_once"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<GmailSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            DaybookLogger.i(context, TAG, "Scheduled periodic Gmail sync (15 min interval, battery-not-low)")
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            DaybookLogger.i(context, TAG, "Cancelled periodic Gmail sync")
        }

        fun enqueueOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<GmailSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
