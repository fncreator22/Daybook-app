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
 *  2. Preference signals with reinforcement learning and temporal decay:
 *     - Repeated preferences boost confidence (+0.15 up to 1.0) and increment sample count
 *     - Temporal half-life decay (1 week) lowers weight of old preferences
 *     - Active high-weight preferences are injected directly into cross-session LLM prompts
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
     * Returns recent session summaries and active learned preferences formatted as a
     * compact context string for cross-session LLM prompt injection (≤600 chars total).
     */
    fun recentContext(db: DaybookDatabase, limit: Int = 3): String {
        val summaries = ConversationDao(db).recentSummaries(limit)
        val summariesText = summaries.joinToString(" | ") { it.summary }
        val preferencesText = getTopPreferencesContext(db)

        return buildString {
            if (preferencesText.isNotBlank()) {
                append("User preferences: ").append(preferencesText).append(". ")
            }
            if (summariesText.isNotBlank()) {
                append("Recent sessions: ").append(summariesText)
            }
        }.take(600)
    }

    /**
     * Queries learned user preferences, applies temporal decay, and returns top active ones.
     */
    private fun getTopPreferencesContext(db: DaybookDatabase): String {
        return try {
            val tableExists = db.readableDatabase.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='user_preferences'",
                null,
            ).use { it.count > 0 }
            if (!tableExists) return ""

            val now = System.currentTimeMillis()
            val list = mutableListOf<String>()
            db.readableDatabase.rawQuery(
                "SELECT key, value_text, value_float, confidence, sample_count, updated_at FROM user_preferences",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val key = c.getString(0)
                    val textVal = if (!c.isNull(1)) c.getString(1) else null
                    val floatVal = if (!c.isNull(2)) c.getDouble(2) else null
                    val conf = c.getDouble(3)
                    val count = c.getInt(4)
                    val updated = c.getLong(5)

                    // Temporal half-life decay: 1 week = 7 * 86_400_000 ms
                    val ageWeeks = (now - updated).toDouble() / (7.0 * 86_400_000.0)
                    val decay = 1.0 / (1.0 + ageWeeks)
                    val effectiveWeight = conf * decay

                    if (effectiveWeight >= 0.08) {
                        when {
                            textVal != null -> list.add(textVal)
                            key == "nudge_suppressed" && (floatVal ?: 0.0) > 0.5 -> list.add("no nudges")
                            key == "correction_signal" && count > 2 -> list.add("double check instructions")
                        }
                    }
                }
            }
            list.take(3).joinToString("; ")
        } catch (_: Exception) {
            ""
        }
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
     * `user_preferences` table with reinforcement weighting and confidence updates.
     */
    private fun writePreferenceSignals(session: List<ConversationMessage>, db: DaybookDatabase) {
        val tableExists = db.readableDatabase.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='user_preferences'",
            null,
        ).use { it.count > 0 }
        if (!tableExists) return

        val writable = db.writableDatabase
        val now = System.currentTimeMillis()

        session.filter { it.isUser }.forEach { msg ->
            val text = msg.text.lowercase()
            when {
                "don't remind" in text || "stop reminding" in text ->
                    writable.execSQL(
                        "INSERT OR REPLACE INTO user_preferences" +
                            "(key, value_float, confidence, sample_count, updated_at)" +
                            " VALUES ('nudge_suppressed', 1.0, 0.4, 1, ?)",
                        arrayOf<Any>(now),
                    )

                "that's wrong" in text || "not right" in text || "wrong" in text -> run {
                    writable.execSQL(
                        "INSERT OR IGNORE INTO user_preferences" +
                            "(key, value_float, confidence, sample_count, updated_at)" +
                            " VALUES ('correction_signal', 0.0, 0.2, 0, ?)",
                        arrayOf<Any>(now),
                    )
                    writable.execSQL(
                        "UPDATE user_preferences SET" +
                            " value_float = value_float + 1.0," +
                            " sample_count = sample_count + 1," +
                            " confidence = MIN(1.0, confidence + 0.15)," +
                            " updated_at = ?" +
                            " WHERE key = 'correction_signal'",
                        arrayOf<Any>(now),
                    )
                }

                text.startsWith("i prefer") || "prefer " in text -> {
                    val rawPref = msg.text.take(120)
                    val key = "expressed_preference:${rawPref.trim().lowercase().hashCode()}"

                    // Check if already learned to reinforce confidence
                    var existingConf = 0.0
                    var existingCount = 0
                    var found = false

                    writable.rawQuery(
                        "SELECT confidence, sample_count FROM user_preferences WHERE key = ?",
                        arrayOf(key),
                    ).use { c ->
                        if (c.moveToNext()) {
                            found = true
                            existingConf = c.getDouble(0)
                            existingCount = c.getInt(1)
                        }
                    }

                    if (found) {
                        val newConf = minOf(1.0, existingConf + 0.15)
                        val newCount = existingCount + 1
                        writable.execSQL(
                            "UPDATE user_preferences SET confidence = ?, sample_count = ?, updated_at = ? WHERE key = ?",
                            arrayOf<Any>(newConf, newCount, now, key),
                        )
                    } else {
                        writable.execSQL(
                            "INSERT INTO user_preferences (key, value_text, confidence, sample_count, updated_at) VALUES (?, ?, 0.3, 1, ?)",
                            arrayOf<Any>(key, rawPref, now),
                        )
                    }
                }
            }
        }
    }
}
