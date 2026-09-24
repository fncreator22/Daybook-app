package com.sr2ma.daybook.domain.model

/**
 * A compact record of what one conversation session was about.
 *
 * Cache-tier: stored in the `conversation_summaries` table. Clearing this table
 * (via Settings → "Clear conversation history" or Android's app-cache clear) has
 * no effect on tasks, meetings, logs, or wallet passes.
 *
 * [summary] is at most 200 characters.
 * [entities] is a comma-separated list of proper nouns / key phrases extracted
 * from the session, e.g. "Rahul,Design project,Thursday 3pm".
 */
data class ConversationSummary(
    val id: Long = 0,
    val summary: String,
    val entities: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
