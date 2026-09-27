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
 *   v1 Ã¢â€ â€™ v2: tasks Ã¢â‚¬â€ cadence, cadence_parent_id, cadence_last_generated
 *   v2 Ã¢â€ â€™ v3: meetings Ã¢â‚¬â€ ai_summary, ai_summary_at
 *   v3 Ã¢â€ â€™ v4: meetings Ã¢â‚¬â€ calendar_event_id
 *   v4 Ã¢â€ â€™ v5: passes table (barcode wallet)
 *   v5 Ã¢â€ â€™ v6: meetings + tasks Ã¢â‚¬â€ gcal_event_id, sync_status, tasks.calendar_sync_enabled
 */
class DaybookDatabase(val context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        // Needed for tasks.meeting_id ON DELETE SET NULL to actually fire.
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        SCHEMA.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never drop a table Ã¢â‚¬â€ this is the user's only copy of the data.
        if (oldVersion < 2) MIGRATIONS_V2.forEach(db::execSQL)
        if (oldVersion < 3) MIGRATIONS_V3.forEach(db::execSQL)
        if (oldVersion < 4) MIGRATIONS_V4.forEach(db::execSQL)
        if (oldVersion < 5) MIGRATIONS_V5.forEach(db::execSQL)
        if (oldVersion < 6) MIGRATIONS_V6.forEach(db::execSQL)
        if (oldVersion < 7) MIGRATIONS_V7.forEach(db::execSQL)
        if (oldVersion < 8) MIGRATIONS_V8.forEach(db::execSQL)
        if (oldVersion < 9) MIGRATIONS_V9.forEach(db::execSQL)
        if (oldVersion < 10) MIGRATIONS_V10.forEach(db::execSQL)
        if (oldVersion < 11) MIGRATIONS_V11.forEach(db::execSQL)
    }

    companion object {
        const val DATABASE_NAME = "daybook.db"
        const val DATABASE_VERSION = 11

        const val TABLE_MEETINGS = "meetings"
        const val TABLE_TASKS = "tasks"
        const val TABLE_LOG_ENTRIES = "log_entries"
        const val TABLE_PASSES = "passes"
        const val TABLE_WHATSAPP = "whatsapp_messages"
        const val TABLE_CONVERSATION_SUMMARIES = "conversation_summaries"
        const val TABLE_USER_PREFERENCES = "user_preferences"
        const val TABLE_GMAIL = "gmail_messages"

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
                is_favorited INTEGER NOT NULL DEFAULT 0,
                is_archived INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE whatsapp_messages (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                sender           TEXT    NOT NULL,
                message          TEXT    NOT NULL,
                received_at      INTEGER NOT NULL,
                replied_text     TEXT,
                replied_at       INTEGER,
                notification_key TEXT,
                created_at       INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE kg_nodes (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_text TEXT    NOT NULL,
                source_id   INTEGER NOT NULL,
                source_type TEXT    NOT NULL,
                last_seen   INTEGER NOT NULL DEFAULT (strftime('%s','now'))
            )
            """.trimIndent(),
            """
            CREATE TABLE kg_edges (
                from_node INTEGER NOT NULL REFERENCES kg_nodes(id) ON DELETE CASCADE,
                to_node   INTEGER NOT NULL REFERENCES kg_nodes(id) ON DELETE CASCADE,
                relation  TEXT    NOT NULL,
                weight    REAL    NOT NULL DEFAULT 1.0,
                PRIMARY KEY (from_node, to_node, relation)
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
            "CREATE INDEX IF NOT EXISTS idx_passes_is_favorited ON passes(is_favorited)",
            "CREATE INDEX IF NOT EXISTS idx_passes_is_archived ON passes(is_archived)",
            "CREATE INDEX idx_whatsapp_sender ON whatsapp_messages(sender)",
            "CREATE INDEX idx_whatsapp_received ON whatsapp_messages(received_at)",
            "CREATE INDEX idx_kg_edges_from ON kg_edges(from_node)",
            "CREATE INDEX idx_kg_edges_to   ON kg_edges(to_node)",
            // v9: conversation memory (cache-tier) + user preference model
            """
            CREATE TABLE conversation_summaries (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                summary    TEXT    NOT NULL,
                entities   TEXT    NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE user_preferences (
                key          TEXT    PRIMARY KEY,
                value_float  REAL,
                value_text   TEXT,
                confidence   REAL    NOT NULL DEFAULT 0.1,
                sample_count INTEGER NOT NULL DEFAULT 1,
                updated_at   INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE gmail_messages (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                message_id       TEXT    UNIQUE,
                sender           TEXT    NOT NULL,
                subject          TEXT    NOT NULL,
                snippet          TEXT    NOT NULL,
                received_at      INTEGER NOT NULL,
                is_read          INTEGER NOT NULL DEFAULT 0,
                category         TEXT    NOT NULL DEFAULT 'primary',
                suggested_action TEXT,
                actioned_at      INTEGER
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_gmail_received ON gmail_messages(received_at DESC)",
        )

        // Ã¢â€â‚¬Ã¢â€â‚¬ Incremental migrations (applied in onUpgrade) Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

        /** v1 Ã¢â€ â€™ v2: recurring task support */
        val MIGRATIONS_V2: List<String> = listOf(
            "ALTER TABLE tasks ADD COLUMN cadence TEXT NOT NULL DEFAULT 'NONE'",
            "ALTER TABLE tasks ADD COLUMN cadence_parent_id INTEGER",
            "ALTER TABLE tasks ADD COLUMN cadence_last_generated TEXT",
            "CREATE INDEX idx_tasks_cadence ON tasks(cadence)",
        )

        /** v2 Ã¢â€ â€™ v3: AI meeting summaries */
        val MIGRATIONS_V3: List<String> = listOf(
            "ALTER TABLE meetings ADD COLUMN ai_summary TEXT",
            "ALTER TABLE meetings ADD COLUMN ai_summary_at INTEGER",
        )

        /** v3 Ã¢â€ â€™ v4: calendar integration */
        val MIGRATIONS_V4: List<String> = listOf(
            "ALTER TABLE meetings ADD COLUMN calendar_event_id TEXT",
        )

        /** v4 Ã¢â€ â€™ v5: barcode wallet (ADR-0002 Ã¢â‚¬â€ passes have barcodes) */
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

        /** v5 Ã¢â€ â€™ v6: Google Calendar sync columns on meetings and tasks */
        val MIGRATIONS_V6: List<String> = listOf(
            "ALTER TABLE meetings ADD COLUMN gcal_event_id TEXT",
            "ALTER TABLE meetings ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY'",
            "ALTER TABLE tasks ADD COLUMN gcal_event_id TEXT",
            "ALTER TABLE tasks ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY'",
            "ALTER TABLE tasks ADD COLUMN calendar_sync_enabled INTEGER NOT NULL DEFAULT 0",
        )

        /** v6 Ã¢â€ â€™ v7: WhatsApp notification log (Phase 7, opt-in) */
        val MIGRATIONS_V7: List<String> = listOf(
            """
            CREATE TABLE whatsapp_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                message TEXT NOT NULL,
                received_at INTEGER NOT NULL,
                replied_text TEXT,
                replied_at INTEGER,
                notification_key TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX idx_whatsapp_sender ON whatsapp_messages(sender)",
            "CREATE INDEX idx_whatsapp_received ON whatsapp_messages(received_at)",
        )


        /** v7 → v8: OKF knowledge graph — node and edge tables for dot-to-dot reasoning */
        val MIGRATIONS_V8: List<String> = listOf(
            """
            CREATE TABLE IF NOT EXISTS kg_nodes (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                entity_text TEXT    NOT NULL,
                source_id   INTEGER NOT NULL,
                source_type TEXT    NOT NULL,
                last_seen   INTEGER NOT NULL DEFAULT (strftime('%s','now'))
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS kg_edges (
                from_node INTEGER NOT NULL REFERENCES kg_nodes(id) ON DELETE CASCADE,
                to_node   INTEGER NOT NULL REFERENCES kg_nodes(id) ON DELETE CASCADE,
                relation  TEXT    NOT NULL,
                weight    REAL    NOT NULL DEFAULT 1.0,
                PRIMARY KEY (from_node, to_node, relation)
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_kg_edges_from ON kg_edges(from_node)",
            "CREATE INDEX IF NOT EXISTS idx_kg_edges_to   ON kg_edges(to_node)",
        )

        /** v8 → v9: conversation memory (cache-tier) + user preference model */
        val MIGRATIONS_V9: List<String> = listOf(
            """
            CREATE TABLE IF NOT EXISTS conversation_summaries (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                summary    TEXT    NOT NULL,
                entities   TEXT    NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS user_preferences (
                key          TEXT    PRIMARY KEY,
                value_float  REAL,
                value_text   TEXT,
                confidence   REAL    NOT NULL DEFAULT 0.1,
                sample_count INTEGER NOT NULL DEFAULT 1,
                updated_at   INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        /** v9 → v10: on-device Gmail message cache + action suggestions */
        val MIGRATIONS_V10: List<String> = listOf(
            """
            CREATE TABLE IF NOT EXISTS gmail_messages (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                message_id       TEXT    UNIQUE,
                sender           TEXT    NOT NULL,
                subject          TEXT    NOT NULL,
                snippet          TEXT    NOT NULL,
                received_at      INTEGER NOT NULL,
                is_read          INTEGER NOT NULL DEFAULT 0,
                category         TEXT    NOT NULL DEFAULT 'primary',
                suggested_action TEXT,
                actioned_at      INTEGER
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_gmail_received ON gmail_messages(received_at DESC)",
        )

        /** v10 → v11: wallet pass favorites and archiving support */
        val MIGRATIONS_V11: List<String> = listOf(
            "ALTER TABLE passes ADD COLUMN is_favorited INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE passes ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0",
            "CREATE INDEX IF NOT EXISTS idx_passes_is_favorited ON passes(is_favorited)",
            "CREATE INDEX IF NOT EXISTS idx_passes_is_archived ON passes(is_archived)",
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

        /**
         * Checks if SQLCipher native libraries are available in this runtime environment.
         */
        fun isSqlCipherAvailable(context: Context? = null): Boolean {
            return try {
                System.loadLibrary("sqlcipher")
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    /**
     * Retrieves the 256-bit database encryption key securely managed via Android Keystore.
     */
    fun getDatabasePassphrase(): ByteArray = DatabaseKeyManager.getOrCreateDatabaseKey(context)

    /**
     * Checks if the physical database file is encrypted or plaintext SQLite.
     * Plaintext SQLite files start with the 16-byte header: "SQLite format 3\0".
     * SQLCipher encrypted files do not contain this plaintext header.
     */
    fun isEncrypted(): Boolean {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        return isEncrypted(dbFile)
    }

    fun isEncrypted(file: java.io.File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(16)
        return try {
            file.inputStream().use { it.read(header) }
            val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            !header.contentEquals(sqliteHeader)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Migrates a legacy plaintext database file to an encrypted SQLCipher database if native
     * SQLCipher libraries are available and the database is currently plaintext.
     *
     * Returns true if the database is encrypted or successfully migrated, false otherwise.
     */
    fun migratePlaintextToEncrypted(): Boolean {
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        if (!dbFile.exists() || isEncrypted(dbFile)) return true
        if (!isSqlCipherAvailable(context)) return false

        val passphrase = getDatabasePassphrase()
        return try {
            val encryptedTemp = java.io.File(dbFile.parentFile, "$DATABASE_NAME.enc_tmp")
            if (encryptedTemp.exists()) encryptedTemp.delete()

            val dbClass = Class.forName("net.zetetic.database.sqlcipher.SQLiteDatabase")
            val openMethod = dbClass.getMethod(
                "openOrCreateDatabase",
                java.io.File::class.java,
                Class.forName("net.zetetic.database.sqlcipher.SQLiteDatabase\$CursorFactory"),
            )
            val plaintextDb = openMethod.invoke(null, dbFile, null)
            val rawExecMethod = dbClass.getMethod("rawExecSQL", String::class.java)
            val closeMethod = dbClass.getMethod("close")

            val hexKey = passphrase.joinToString("") { "%02x".format(it) }
            rawExecMethod.invoke(plaintextDb, "ATTACH DATABASE '${encryptedTemp.absolutePath}' AS encrypted KEY \"x'$hexKey'\";")
            rawExecMethod.invoke(plaintextDb, "SELECT sqlcipher_export('encrypted');")
            rawExecMethod.invoke(plaintextDb, "DETACH DATABASE encrypted;")
            closeMethod.invoke(plaintextDb)

            // Atomic rename
            val backupPlaintext = java.io.File(dbFile.parentFile, "$DATABASE_NAME.plaintext_bak")
            if (backupPlaintext.exists()) backupPlaintext.delete()
            if (dbFile.renameTo(backupPlaintext)) {
                if (encryptedTemp.renameTo(dbFile)) {
                    backupPlaintext.delete()
                    true
                } else {
                    backupPlaintext.renameTo(dbFile)
                    false
                }
            } else {
                encryptedTemp.delete()
                false
            }
        } catch (_: Throwable) {
            false
        } finally {
            DatabaseKeyManager.wipe(passphrase)
        }
    }
}

