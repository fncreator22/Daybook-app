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
