package com.sr2ma.daybook.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Daybook talks to SQLite directly through [SQLiteOpenHelper] rather than Room.
 *
 * That is a deliberate trade: Room would add an annotation processor (KSP) whose
 * version has to track the Kotlin version exactly, and it is the single most
 * common reason a project fails to sync after a toolchain bump. The schema here
 * is three tables with no joins deeper than one level, so hand-written DAOs cost
 * little and the module has zero code-generation steps.
 *
 * Every query in [com.sr2ma.daybook.data.dao] is parameterised. No SQL is built
 * by string concatenation with user input.
 *
 * Migration history:
 *   v1 → v2: tasks — cadence, cadence_parent_id, cadence_last_generated
 *   v2 → v3: meetings — ai_summary, ai_summary_at
 *   v3 → v4: meetings — calendar_event_id
 *   v4 → v5: passes table (barcode wallet)
 *   v5 → v6: meetings + tasks — gcal_event_id, sync_status, tasks.calendar_sync_enabled
 */
class DaybookDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        // Needed for tasks.meeting_id ON DELETE SET NULL to actually fire.
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        SCHEMA.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never drop a table — this is the user's only copy of the data.
        if (oldVersion < 2) MIGRATIONS_V2.forEach(db::execSQL)
        if (oldVersion < 3) MIGRATIONS_V3.forEach(db::execSQL)
        if (oldVersion < 4) MIGRATIONS_V4.forEach(db::execSQL)
        if (oldVersion < 5) MIGRATIONS_V5.forEach(db::execSQL)
        if (oldVersion < 6) MIGRATIONS_V6.forEach(db::execSQL)
    }

    companion object {
        const val DATABASE_NAME = "daybook.db"
        const val DATABASE_VERSION = 6

        const val TABLE_MEETINGS = "meetings"
        const val TABLE_TASKS = "tasks"
        const val TABLE_LOG_ENTRIES = "log_entries"
        const val TABLE_PASSES = "passes"

        /**
         * Statements are ordered so `meetings` exists before `tasks` references
         * it. Kept as plain strings so the schema can be executed and checked
         * outside of Android.
         */
        val SCHEMA: List<String> = listOf(
            """
            CREATE TABLE meetings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                attendees TEXT NOT NULL DEFAULT '',
                day TEXT NOT NULL,
                start_time TEXT,
                location TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                next_touch TEXT,
                follow_up_done INTEGER NOT NULL DEFAULT 0,
                ai_summary TEXT,
                ai_summary_at INTEGER,
                calendar_event_id TEXT,
                gcal_event_id TEXT,
                sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                priority INTEGER NOT NULL DEFAULT 1,
                status TEXT NOT NULL DEFAULT 'OPEN',
                due_date TEXT,
                project TEXT,
                meeting_id INTEGER REFERENCES meetings(id) ON DELETE SET NULL,
                cadence TEXT NOT NULL DEFAULT 'NONE',
                cadence_parent_id INTEGER,
                cadence_last_generated TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                completed_at INTEGER,
                gcal_event_id TEXT,
                sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                calendar_sync_enabled INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE log_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                day TEXT NOT NULL,
                body TEXT NOT NULL,
                kind TEXT NOT NULL DEFAULT 'NOTE',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE passes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'OTHER',
                barcode_value TEXT NOT NULL,
                barcode_format TEXT NOT NULL,
                ocr_text TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                expiry_date TEXT,
                balance TEXT,
                image_path TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX idx_tasks_status ON tasks(status)",
            "CREATE INDEX idx_tasks_due_date ON tasks(due_date)",
            "CREATE INDEX idx_tasks_meeting_id ON tasks(meeting_id)",
            "CREATE INDEX idx_tasks_cadence ON tasks(cadence)",
            "CREATE INDEX idx_log_entries_day ON log_entries(day)",
            "CREATE INDEX idx_meetings_day ON meetings(day)",
            "CREATE INDEX idx_meetings_next_touch ON meetings(next_touch)",
            "CREATE INDEX idx_passes_category ON passes(category)",
        )

        // ── Incremental migrations (applied in onUpgrade) ─────────────────

        /** v1 → v2: recurring task support */
        val MIGRATIONS_V2: List<String> = listOf(
            "ALTER TABLE tasks ADD COLUMN cadence TEXT NOT NULL DEFAULT 'NONE'",
            "ALTER TABLE tasks ADD COLUMN cadence_parent_id INTEGER",
            "ALTER TABLE tasks ADD COLUMN cadence_last_generated TEXT",
            "CREATE INDEX idx_tasks_cadence ON tasks(cadence)",
        )

        /** v2 → v3: AI meeting summaries */
        val MIGRATIONS_V3: List<String> = listOf(
            "ALTER TABLE meetings ADD COLUMN ai_summary TEXT",
            "ALTER TABLE meetings ADD COLUMN ai_summary_at INTEGER",
        )

        /** v3 → v4: calendar integration */
        val MIGRATIONS_V4: List<String> = listOf(
            "ALTER TABLE meetings ADD COLUMN calendar_event_id TEXT",
        )

        /** v4 → v5: barcode wallet (ADR-0002 — passes have barcodes) */
        val MIGRATIONS_V5: List<String> = listOf(
            """
            CREATE TABLE passes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'OTHER',
                barcode_value TEXT NOT NULL,
                barcode_format TEXT NOT NULL,
                ocr_text TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                expiry_date TEXT,
                balance TEXT,
                image_path TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX idx_passes_category ON passes(category)",
        )

        /** v5 → v6: Google Calendar sync columns on meetings and tasks */
        val MIGRATIONS_V6: List<String> = listOf(
            "ALTER TABLE meetings ADD COLUMN gcal_event_id TEXT",
            "ALTER TABLE meetings ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY'",
            "ALTER TABLE tasks ADD COLUMN gcal_event_id TEXT",
            "ALTER TABLE tasks ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY'",
            "ALTER TABLE tasks ADD COLUMN calendar_sync_enabled INTEGER NOT NULL DEFAULT 0",
        )

        @Volatile private var instance: DaybookDatabase? = null

        /**
         * Returns the process-wide singleton, creating it if needed.
         * Used by [com.sr2ma.daybook.ai.NightlyAgentWorker] which cannot
         * access [AppContainer] from a Worker context.
         */
        fun getInstance(context: Context): DaybookDatabase =
            instance ?: synchronized(this) {
                instance ?: DaybookDatabase(context.applicationContext).also { instance = it }
            }
    }
}
