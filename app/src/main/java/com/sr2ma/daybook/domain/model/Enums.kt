package com.sr2ma.daybook.domain.model

/**
 * Task importance. [storedValue] is what lands in SQLite; ordering by it
 * ascending puts the calmest work first, so screens that want "most important
 * at the top" sort descending.
 */
enum class Priority(val storedValue: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2),
    URGENT(3),
    ;

    companion object {
        fun fromStored(value: Int): Priority =
            entries.firstOrNull { it.storedValue == value } ?: MEDIUM
    }
}

/** Where a task is in its life. */
enum class TaskStatus(val storedValue: String) {
    OPEN("OPEN"),
    IN_PROGRESS("IN_PROGRESS"),
    DONE("DONE"),
    CANCELLED("CANCELLED"),
    ;

    /** True once the task should stop showing up as work to do. */
    val isClosed: Boolean get() = this == DONE || this == CANCELLED

    companion object {
        fun fromStored(value: String?): TaskStatus =
            entries.firstOrNull { it.storedValue == value } ?: OPEN
    }
}

/** What kind of thing a daily-log line records. */
enum class LogKind(val storedValue: String) {
    NOTE("NOTE"),
    DECISION("DECISION"),
    BLOCKER("BLOCKER"),
    WIN("WIN"),
    ;

    companion object {
        fun fromStored(value: String?): LogKind =
            entries.firstOrNull { it.storedValue == value } ?: NOTE
    }
}

/** How often a recurring task repeats. NONE = not recurring. */
enum class Cadence(val storedValue: String) {
    NONE("NONE"),
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY"),
    ;

    companion object {
        fun fromStored(value: String?): Cadence =
            entries.firstOrNull { it.storedValue == value } ?: NONE
    }
}

/** Types of proactive suggestions produced by AgentEngine. */
enum class SuggestionType(val storedValue: String) {
    STALE_TASK("STALE_TASK"),
    FOLLOW_UP_DUE("FOLLOW_UP_DUE"),
    CADENCE_DUE("CADENCE_DUE"),
    ;

    companion object {
        fun fromStored(value: String?): SuggestionType? =
            entries.firstOrNull { it.storedValue == value }
    }
}

/** Category of a Pass in the barcode wallet (ADR-0002). */
enum class PassCategory(val storedValue: String) {
    LOYALTY_CARD("LOYALTY_CARD"),
    EVENT_TICKET("EVENT_TICKET"),
    TRANSPORT("TRANSPORT"),
    GIFT_CARD("GIFT_CARD"),
    ID("ID"),
    HEALTH("HEALTH"),
    OTHER("OTHER"),
    ;

    companion object {
        fun fromStored(value: String?): PassCategory =
            entries.firstOrNull { it.storedValue == value } ?: OTHER
    }
}

/**
 * Where a meeting or task sits in the Google Calendar sync pipeline.
 * LOCAL_ONLY = created locally, not yet eligible for sync.
 * PENDING_SYNC = user enabled sync; queued for next WorkManager run.
 * SYNCED = successfully pushed; gcal_event_id is populated.
 * SYNC_ERROR = last sync attempt failed; can be retried.
 */
enum class SyncStatus(val storedValue: String) {
    LOCAL_ONLY("LOCAL_ONLY"),
    PENDING_SYNC("PENDING_SYNC"),
    SYNCED("SYNCED"),
    SYNC_ERROR("SYNC_ERROR"),
    ;

    companion object {
        fun fromStored(value: String?): SyncStatus =
            entries.firstOrNull { it.storedValue == value } ?: LOCAL_ONLY
    }
}

/**
 * How much autonomy the AI agent has when executing a tool in a given category.
 *
 * ASK_EVERY_TIME  — default for anything that writes off-device. Agent surfaces a
 *                   confirmation before acting.
 * AUTO_SESSION    — approved for this app session only; resets on next cold start.
 * ALWAYS_AUTO     — user permanently opted in; agent acts without asking.
 *
 * Per architecture doc §8: default must be ASK_EVERY_TIME for Calendar, Drive,
 * Gmail and Wallet writes.
 */
enum class AutonomyLevel(val storedValue: String, val label: String) {
    ASK_EVERY_TIME("ASK", "Ask"),
    AUTO_SESSION("SESSION", "Session"),
    ALWAYS_AUTO("ALWAYS", "Always"),
    ;

    companion object {
        fun fromStored(value: String?): AutonomyLevel =
            entries.firstOrNull { it.storedValue == value } ?: ASK_EVERY_TIME
    }
}

/** Tool categories whose autonomy level is configurable by the user. */
enum class ToolCategory(val storedValue: String, val label: String) {
    CALENDAR("CALENDAR", "Calendar"),
    DRIVE("DRIVE", "Drive"),
    GMAIL("GMAIL", "Gmail"),
    WALLET("WALLET", "Wallet"),
    ;
}
