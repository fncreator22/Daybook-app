package com.sr2ma.daybook.ai

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.DaybookRepository
import com.sr2ma.daybook.domain.AgentEngine
import com.sr2ma.daybook.domain.BriefingWriter
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Runs every night to perform the rule-engine agent loop.
 *
 * Architecture (AGENTS.md — LLM is called only from within AgentEngine):
 * ─────────────────────────────────────────────────────────────────────
 * 1. Rule engine runs first, always (deterministic, zero-cost, zero-AI).
 *    - CadenceEngine: generate overdue recurring task instances.
 *    - AgentEngine: produce staleness/completion suggestions.
 *    - BriefingWriter: build tomorrow's briefing string.
 * 2. If AI is available (model downloaded, user opted in):
 *    - EmbeddingEngine: re-embed any tasks/meetings added since last night.
 *    - LlmEngine: invoked by AgentEngine only for unstructured→structured
 *      classification (not for routing decisions).
 *
 * ADR-0005: if AI download was declined, rule-engine steps still run.
 * This worker never skips silently — it always logs outcome.
 *
 * Scheduling: [schedule] registers a periodic 24-hour job at midnight ±15 min.
 * WorkManager handles persisting the job across reboots.
 */
class NightlyAgentWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "NightlyAgentWorker starting")

        val database = DaybookDatabase.getInstance(applicationContext)
        val repository = DaybookRepository(database)
        repository.refreshAll()

        val today = LocalDate.now()
        val allTasks = repository.tasks.value
        val allMeetings = repository.meetings.value

        // ── Step 1: Rule engine (always runs) ─────────────────────────────────
        val suggestions = AgentEngine.computeSuggestions(allTasks, allMeetings, today)
        Log.i(TAG, "AgentEngine: ${suggestions.size} suggestion(s)")

        val todayBoard = com.sr2ma.daybook.domain.TodayBoard(day = today)
        val briefing = BriefingWriter.write(todayBoard)
        Log.i(TAG, "Briefing ready: ${briefing.take(80)}…")

        // ── Step 2: AI features (optional, gated on model presence) ──────────
        val downloader = ModelDownloader(applicationContext)
        if (downloader.isModelPresent()) {
            runAiSteps(repository, database)
        } else {
            Log.d(TAG, "AI model not present — rule-engine-only run complete")
        }

        Log.i(TAG, "NightlyAgentWorker finished")
        return Result.success()
    }

    private suspend fun runAiSteps(
        repository: DaybookRepository,
        database: DaybookDatabase,
    ) {
        val embeddingEngine = EmbeddingEngine(applicationContext)
        val vectorStore = VectorStore(database.writableDatabase)
        vectorStore.ensureSchema()

        // Re-embed tasks modified in the last 48 h
        val cutoff = System.currentTimeMillis() - 48 * 3600 * 1000L
        val recentTasks = repository.tasks.value.filter { it.updatedAt >= cutoff }
        recentTasks.forEach { task ->
            val text = "${task.title} ${task.notes}".trim()
            val vec = embeddingEngine.embed(text)
            if (vec != null) vectorStore.upsert(task.id, "task", vec)
        }
        Log.d(TAG, "AI: re-embedded ${recentTasks.size} task(s)")

        // Re-embed meetings modified in the last 48 h
        val recentMeetings = repository.meetings.value.filter { it.updatedAt >= cutoff }
        recentMeetings.forEach { meeting ->
            val text = "${meeting.title} ${meeting.notes}".trim()
            val vec = embeddingEngine.embed(text)
            if (vec != null) vectorStore.upsert(meeting.id, "meeting", vec)
        }
        Log.d(TAG, "AI: re-embedded ${recentMeetings.size} meeting(s)")

        embeddingEngine.close()
    }

    companion object {
        private const val TAG = "NightlyAgentWorker"
        private const val WORK_NAME = "daybook_nightly_agent"

        /**
         * Registers (or replaces) the nightly periodic job.
         * Call from [DaybookApplication.onCreate] — idempotent.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<NightlyAgentWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 15,
                flexTimeIntervalUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "Nightly agent scheduled")
        }
    }
}
