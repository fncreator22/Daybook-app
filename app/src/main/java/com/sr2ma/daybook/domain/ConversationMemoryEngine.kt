package com.sr2ma.daybook.domain

import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.data.dao.ConversationDao
import com.sr2ma.daybook.domain.model.ConversationSummary

/**
 * Extracts signals from a closed conversation session and writes them to the DB.
 *
 * Pure object — no Android UI. The caller must provide a [DaybookDatabase]
 * and run this on the IO dispatcher.
 *
 * What is extracted:
 *  1. Named entities (proper nouns, dates, times) → comma-separated string
 *  2. Preference signals (keyword patterns) → written to user_preferences if the table exists
 *  3. One-line session summary (≤200 chars) → written to conversation_summaries
 *
 * The full conversation text is **never** written to disk.
 */
object ConversationMemoryEngine {

    private val DATE_PATTERN = Regex(
        "\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
            "today|tomorrow|\\d{1,2}(am|pm|:\\d{2}))\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Runs the extraction pass when a session closes.
     * Writes a [ConversationSummary] row and any preference signals, then returns
     * the summary so the caller can inspect it if needed.
     */
    fun extract(
        session: List<ConversationMessage>,
        db: DaybookDatabase,
    ): ConversationSummary {
        if (session.isEmpty()) {
            return ConversationSummary(summary = "Empty session", entities = "")
        }

        val entities = extractEntities(session)
        val summary = summarize(session, entities)
        val result = ConversationSummary(
            summary = summary,
            entities = entities.joinToString(","),
        )

        val dao = ConversationDao(db)
        dao.insertSummary(result)           // also prunes ring buffer to 50 rows
        writePreferenceSignals(session, db)
        return result
    }

    /**
     * Returns recent session summaries formatted as a compact context string
     * for LLM prompt injection (≤600 chars total).
     */
    fun recentContext(db: DaybookDatabase, limit: Int = 3): String {
        val summaries = ConversationDao(db).recentSummaries(limit)
        return summaries
            .joinToString(" | ") { it.summary }
            .take(600)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts up to 10 unique entity strings from the session:
     * - Date/time patterns (Monday, 3pm, tomorrow, etc.)
     * - Tokens starting with an uppercase letter and longer than 2 chars that
     *   do not immediately follow a sentence-ending punctuation mark.
     */
    private fun extractEntities(session: List<ConversationMessage>): List<String> {
        val found = mutableSetOf<String>()

        // Date/time patterns
        session.flatMap { DATE_PATTERN.findAll(it.text).map { m -> m.value } }
            .map { it.replaceFirstChar { c -> c.uppercase() } }
            .forEach { found += it }

        // Proper-noun heuristic: capitalized token > 2 chars, not at sentence start
        session.forEach { msg ->
            val tokens = msg.text.split(Regex("\\s+"))
            tokens.windowed(2).forEach { (prev, word) ->
                if (word.length > 2 &&
                    word[0].isUpperCase() &&
                    !prev.endsWith('.') && !prev.endsWith('?') && !prev.endsWith('!')
                ) {
                    found += word.trimEnd(',', '.', '!', '?')
                }
            }
        }

        return found.take(10).toList()
    }

    /**
     * Produces a ≤200-char plain-text summary of the session.
     */
    private fun summarize(session: List<ConversationMessage>, entities: List<String>): String {
        val firstUser = session.firstOrNull { it.isUser }?.text ?: "Conversation"
        val base = if (entities.isNotEmpty()) {
            "User discussed ${entities.take(3).joinToString(", ")}: ${firstUser.take(80)}"
        } else {
            firstUser.take(150)
        }
        return base.take(200)
    }

    /**
     * Writes preference signals extracted from user messages to the
     * `user_preferences` table. Silently skipped if the table does not exist yet.
     */
    private fun writePreferenceSignals(session: List<ConversationMessage>, db: DaybookDatabase) {
        // Guard: check that user_preferences table exists before writing
        val tableExists = db.readableDatabase.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='user_preferences'",
            null,
        ).use { it.count > 0 }
        if (!tableExists) return

        val writable = db.writableDatabase
        session.filter { it.isUser }.forEach { msg ->
            val text = msg.text.lowercase()
            when {
                "don't remind" in text || "stop reminding" in text ->
                    writable.execSQL(
                        "INSERT OR REPLACE INTO user_preferences" +
                            "(key, value_float, confidence, sample_count, updated_at)" +
                            " VALUES ('nudge_suppressed', 1.0, 0.1, 1, ?)",
                        arrayOf<Any>(System.currentTimeMillis()),
                    )

                "that's wrong" in text || "not right" in text || "wrong" in text -> run {
                    // INSERT OR IGNORE seeds the row the first time; UPDATE increments it.
                    writable.execSQL(
                        "INSERT OR IGNORE INTO user_preferences" +
                            "(key, value_float, confidence, sample_count, updated_at)" +
                            " VALUES ('correction_signal', 0.0, 0.1, 0, ?)",
                        arrayOf<Any>(System.currentTimeMillis()),
                    )
                    writable.execSQL(
                        "UPDATE user_preferences SET" +
                            " value_float = value_float + 1.0," +
                            " sample_count = sample_count + 1," +
                            " updated_at = ?" +
                            " WHERE key = 'correction_signal'",
                        arrayOf<Any>(System.currentTimeMillis()),
                    )
                }

                text.startsWith("i prefer") || "prefer " in text ->
                    writable.execSQL(
                        "INSERT OR REPLACE INTO user_preferences" +
                            "(key, value_text, confidence, sample_count, updated_at)" +
                            " VALUES (?, ?, 0.1, 1, ?)",
                        arrayOf<Any>(
                            "expressed_preference:${msg.text.take(40).hashCode()}",
                            msg.text.take(120),
                            System.currentTimeMillis(),
                        ),
                    )
            }
        }
    }
}
