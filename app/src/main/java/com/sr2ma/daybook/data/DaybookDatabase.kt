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
        // Version 1 is the first release, so there is nothing to migrate yet.
        // Future versions add `if (oldVersion < n) { ... }` blocks here and must
        // never drop a table, because this is the user's only copy of the data.
    }

    companion object {
        const val DATABASE_NAME = "daybook.db"
        const val DATABASE_VERSION = 1

        const val TABLE_MEETINGS = "meetings"
        const val TABLE_TASKS = "tasks"
        const val TABLE_LOG_ENTRIES = "log_entries"

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
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                completed_at INTEGER
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
            "CREATE INDEX idx_tasks_status ON tasks(status)",
            "CREATE INDEX idx_tasks_due_date ON tasks(due_date)",
            "CREATE INDEX idx_tasks_meeting_id ON tasks(meeting_id)",
            "CREATE INDEX idx_log_entries_day ON log_entries(day)",
            "CREATE INDEX idx_meetings_day ON meetings(day)",
            "CREATE INDEX idx_meetings_next_touch ON meetings(next_touch)",
        )
    }
}
