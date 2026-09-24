package com.sr2ma.daybook.domain

/**
 * Manages the in-RAM list of messages for one conversation session.
 *
 * Nothing here ever touches disk. The full session list is discarded when
 * [clear] is called (i.e. when the sheet closes). Only the extracted signals
 * produced by [ConversationMemoryEngine] reach the database.
 *
 * Safety limits (all enforced silently — no error surfaces to the user):
 * - Max [MAX_MESSAGES] messages per session.
 * - User input truncated to [MAX_INPUT_CHARS] characters.
 * - Rate limit: [RATE_LIMIT_COUNT] user messages per [RATE_LIMIT_WINDOW_MS].
 * - Duplicate user text within 60 s is silently ignored.
 */
object ConversationSession {

    const val MAX_MESSAGES = 20
    const val MAX_INPUT_CHARS = 500
    const val RATE_LIMIT_COUNT = 5
    const val RATE_LIMIT_WINDOW_MS = 10_000L
    private const val DEDUP_WINDOW_MS = 60_000L

    private val _messages = mutableListOf<ConversationMessage>()
    val messages: List<ConversationMessage> get() = _messages.toList()

    /** Timestamps of recent user messages for rate-limit sliding window. */
    private val userTimestamps = ArrayDeque<Long>()

    /** Last user text + its timestamp for deduplication. */
    private var lastUserText: String? = null
    private var lastUserTimeMs: Long = 0L

    /**
     * Attempts to add [msg] to the session.
     *
     * @return true if added; false if the session is full, rate-limited, or
     *         the message is a duplicate of the previous user message.
     */
    fun add(msg: ConversationMessage): Boolean {
        if (_messages.size >= MAX_MESSAGES) return false
        val safe = msg.copy(text = msg.text.take(MAX_INPUT_CHARS))
        if (safe.isUser) {
            val now = safe.timestampMs
            // Deduplication: same text within 60 s from user
            if (safe.text == lastUserText && now - lastUserTimeMs < DEDUP_WINDOW_MS) return false
            // Rate limiting: sliding window
            userTimestamps.removeAll { now - it > RATE_LIMIT_WINDOW_MS }
            if (userTimestamps.size >= RATE_LIMIT_COUNT) return false
            userTimestamps.addLast(now)
            lastUserText = safe.text
            lastUserTimeMs = now
        }
        _messages += safe
        return true
    }

    /** Clears all messages and resets rate-limit state. Call when the sheet closes. */
    fun clear() {
        _messages.clear()
        userTimestamps.clear()
        lastUserText = null
        lastUserTimeMs = 0L
    }

    fun isEmpty(): Boolean = _messages.isEmpty()

    /** True while the rate-limit window is active and no more user messages can be added. */
    fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        userTimestamps.removeAll { now - it > RATE_LIMIT_WINDOW_MS }
        return userTimestamps.size >= RATE_LIMIT_COUNT
    }
}
