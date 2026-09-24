package com.sr2ma.daybook.domain

/**
 * A single turn in an agent conversation session.
 *
 * Lives in RAM only — never persisted to disk. The full list is discarded when
 * the session closes; only extracted signals (entities, preferences, a one-line
 * summary) are written to the database by [ConversationMemoryEngine].
 *
 * [suggestions] are action-chip labels shown below an agent bubble, e.g.
 * "Add as task", "Edit", "Dismiss".
 */
data class ConversationMessage(
    val text: String,
    val isUser: Boolean,
    val timestampMs: Long = System.currentTimeMillis(),
    val suggestions: List<String> = emptyList(),
)
