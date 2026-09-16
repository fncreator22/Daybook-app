package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.TaskStatus
import java.time.LocalDate

// ── Public API ────────────────────────────────────────────────────────────────

enum class ParsedIntent {
    CREATE_TASK,
    CREATE_LOG,
    CREATE_MEETING,
    UNKNOWN,
}

data class ParseResult(
    val intent: ParsedIntent,
    // Task fields
    val taskTitle: String? = null,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.OPEN,
    val dueDate: LocalDate? = null,
    // Log fields
    val logBody: String? = null,
    val logKind: LogKind = LogKind.NOTE,
    // Meeting fields
    val meetingTitle: String? = null,
    val meetingAttendees: List<String> = emptyList(),
)

/**
 * Pure text-to-intent parser. No I/O, no Android deps, no LLM.
 *
 * Implements the Ponytail ladder: rung 4 — this is a native-platform-equivalent
 * rule engine that does not need an LLM for keyword-based routing.
 *
 * The LLM is called for capture-and-route ONLY when this parser returns UNKNOWN
 * or when the fields extracted here need validation/enrichment.
 */
object NaturalLanguageParser {

    fun parse(input: String, referenceDate: LocalDate = LocalDate.now()): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ParseResult(intent = ParsedIntent.UNKNOWN)

        val lower = trimmed.lowercase()

        // ── Meeting routing ───────────────────────────────────────────────
        if (lower.startsWith("meeting with") || lower.startsWith("call with")) {
            return parseMeeting(trimmed)
        }

        // ── Log routing ───────────────────────────────────────────────────
        if (lower.startsWith("note:")) {
            return ParseResult(
                intent = ParsedIntent.CREATE_LOG,
                logKind = LogKind.NOTE,
                logBody = trimmed.substringAfter(":").trim(),
            )
        }
        if (lower.startsWith("win:")) {
            return ParseResult(
                intent = ParsedIntent.CREATE_LOG,
                logKind = LogKind.WIN,
                logBody = trimmed.substringAfter(":").trim(),
            )
        }
        if (lower.startsWith("decided") || lower.startsWith("decision:")) {
            return ParseResult(
                intent = ParsedIntent.CREATE_LOG,
                logKind = LogKind.DECISION,
                logBody = trimmed,
            )
        }
        if (lower.startsWith("blocked on") || lower.startsWith("blocker:")) {
            return ParseResult(
                intent = ParsedIntent.CREATE_LOG,
                logKind = LogKind.BLOCKER,
                logBody = trimmed,
            )
        }

        // ── Task routing (default) ────────────────────────────────────────
        val priority = extractPriority(lower)
        val dueDate = extractDueDate(lower, referenceDate)
        // Strip priority/date keywords from the title
        val title = cleanTitle(trimmed)

        return ParseResult(
            intent = ParsedIntent.CREATE_TASK,
            taskTitle = title,
            priority = priority,
            status = TaskStatus.OPEN,
            dueDate = dueDate,
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun parseMeeting(input: String): ParseResult {
        val lower = input.lowercase()
        val withIndex = lower.indexOf(" with ")
        val attendees = if (withIndex != -1) {
            // Extract everything after "with" up to "at" or "on" or end
            val afterWith = input.substring(withIndex + 6)
                .split(Regex("\\bat\\b|\\bon\\b", RegexOption.IGNORE_CASE))
                .first()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            afterWith
        } else emptyList()

        return ParseResult(
            intent = ParsedIntent.CREATE_MEETING,
            meetingTitle = input,
            meetingAttendees = attendees,
        )
    }

    private fun extractPriority(lower: String): Priority = when {
        lower.startsWith("urgent") || lower.startsWith("urgent:") -> Priority.URGENT
        lower.contains("urgent") -> Priority.URGENT
        lower.startsWith("important") || lower.contains("important") -> Priority.HIGH
        lower.startsWith("low priority") || lower.contains("low priority") -> Priority.LOW
        else -> Priority.MEDIUM
    }

    private fun extractDueDate(lower: String, reference: LocalDate): LocalDate? = when {
        lower.contains("today") -> reference
        lower.contains("tomorrow") -> reference.plusDays(1)
        lower.contains("next week") -> reference.plusDays(7)
        lower.contains("next month") -> reference.plusMonths(1)
        else -> null
    }

    /** Remove priority and date keywords from the raw input to form a clean title. */
    private fun cleanTitle(input: String): String {
        return input
            .replace(Regex("^(urgent|important|low priority)[:\\s]+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(today|tomorrow|next week|next month)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .ifEmpty { input.trim() }
    }
}
