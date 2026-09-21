package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.TaskStatus
import java.time.DayOfWeek
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
 * Implements the "rule engine first, LLM only for ambiguous remainder" design
 * documented in AGENTS.md §3. This file owns everything above the LLM call:
 *
 *   "remind me to call mom tomorrow"    → CREATE_TASK  (title="call mom", dueDate=+1d)
 *   "meeting with design team at 3pm"  → CREATE_MEETING (attendees=[design team])
 *   "shipped the auth feature today"   → CREATE_LOG WIN
 *   "blocked on review from backend"   → CREATE_LOG BLOCKER
 *   "urgent: fix crash on login"       → CREATE_TASK URGENT priority
 *
 * The LLM is invoked by the caller when this returns UNKNOWN, or when the
 * caller needs field-level enrichment the rule engine cannot supply.
 */
object NaturalLanguageParser {

    // ── Public entry point ────────────────────────────────────────────────────

    fun parse(input: String, referenceDate: LocalDate = LocalDate.now()): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ParseResult(intent = ParsedIntent.UNKNOWN)
        val lower = trimmed.lowercase()

        // Order matters: most-specific patterns first to avoid false positives.
        detectMeetingIntent(lower, trimmed, referenceDate)?.let { return it }
        detectLogIntent(lower, trimmed)?.let { return it }
        detectExplicitTaskIntent(lower, trimmed, referenceDate)?.let { return it }

        // Default: anything else is a task.
        return ParseResult(
            intent    = ParsedIntent.CREATE_TASK,
            taskTitle = cleanTitle(trimmed),
            priority  = extractPriority(lower),
            status    = TaskStatus.OPEN,
            dueDate   = extractDueDate(lower, referenceDate),
        )
    }

    // ── Meeting detection ─────────────────────────────────────────────────────

    /**
     * Patterns that signal a meeting/call intent.
     * Ordered from most specific to least specific.
     */
    private val MEETING_PREFIXES = listOf(
        // "meeting with X", "call with X", "zoom with X", etc.
        Regex("^(meeting|call|zoom|video call|video chat|catch.?up|1:1|one.on.one|sync|stand.?up|check.?in|debrief|retro|retrospective|interview) with\\b", RegexOption.IGNORE_CASE),
        // "schedule / book / set up a meeting with X"
        Regex("^(schedule|book|set up|arrange|organise|organize|plan) (a |an )?(meeting|call|zoom|video|catch.?up|1:1|sync|stand.?up)\\b", RegexOption.IGNORE_CASE),
        // "have / join / attend a meeting"
        Regex("^(have|join|attend) (a |an )?(meeting|call|zoom|stand.?up)\\b", RegexOption.IGNORE_CASE),
        // "call mom", "call client", "call team" — bare call + noun (no "to" after)
        Regex("^call [a-z](?!.*\\bto\\b)", RegexOption.IGNORE_CASE),
    )

    private fun detectMeetingIntent(lower: String, original: String, reference: LocalDate): ParseResult? {
        if (MEETING_PREFIXES.any { it.containsMatchIn(lower) }) {
            return parseMeeting(original, lower, reference)
        }
        return null
    }

    private fun parseMeeting(original: String, lower: String, reference: LocalDate): ParseResult {
        val withIdx = lower.indexOf(" with ")
        val attendees = if (withIdx != -1) {
            // Grab everything after "with" and stop at "at / on / @" (time/day marker) or end
            original.substring(withIdx + 6)
                .split(Regex("\\bat\\b|\\bon\\b|@|,", RegexOption.IGNORE_CASE))
                .first()
                .split(",")
                .map { it.trim() }
                .filter { it.length > 1 }
        } else emptyList()

        return ParseResult(
            intent           = ParsedIntent.CREATE_MEETING,
            meetingTitle     = original,
            meetingAttendees = attendees,
            dueDate          = extractDueDate(lower, reference),
        )
    }

    // ── Log detection ─────────────────────────────────────────────────────────

    private fun detectLogIntent(lower: String, original: String): ParseResult? {
        // WIN — shipped, launched, completed, etc.
        if (lower.startsWith("win:") || lower.startsWith("win ")) {
            return ParseResult(intent = ParsedIntent.CREATE_LOG, logKind = LogKind.WIN, logBody = original.substringAfter(":").trim())
        }
        if (WIN_STARTS.any { lower.startsWith(it) }) {
            return ParseResult(intent = ParsedIntent.CREATE_LOG, logKind = LogKind.WIN, logBody = original)
        }

        // BLOCKER
        if (BLOCKER_STARTS.any { lower.startsWith(it) }) {
            return ParseResult(intent = ParsedIntent.CREATE_LOG, logKind = LogKind.BLOCKER, logBody = original)
        }

        // DECISION
        if (DECISION_STARTS.any { lower.startsWith(it) }) {
            return ParseResult(intent = ParsedIntent.CREATE_LOG, logKind = LogKind.DECISION, logBody = original)
        }

        // NOTE — generic log entry
        if (NOTE_STARTS.any { lower.startsWith(it) }) {
            val body = if (lower.contains(":")) original.substringAfter(":").trim() else original
            return ParseResult(intent = ParsedIntent.CREATE_LOG, logKind = LogKind.NOTE, logBody = body)
        }

        return null
    }

    private val WIN_STARTS = listOf(
        "shipped", "launched", "released", "deployed", "completed", "finished",
        "accomplished", "delivered", "closed", "resolved", "fixed", "merged",
        "just shipped", "just launched", "just finished", "just completed",
    )
    private val BLOCKER_STARTS = listOf(
        "blocked on", "blocked by", "blocker:", "stuck on", "stuck with",
        "can't proceed", "cannot proceed", "waiting on", "waiting for",
    )
    private val DECISION_STARTS = listOf(
        "decided", "decision:", "agreed to", "going with", "chosen to",
        "we agreed", "team decided", "will use", "we'll use",
    )
    private val NOTE_STARTS = listOf(
        "note:", "log:", "logged", "fyi:", "fyi ", "recorded", "today i",
        "just a note", "reminder:", "memo:", "observation:",
    )

    // ── Explicit task detection ───────────────────────────────────────────────

    /**
     * If the user started with a task-signalling phrase, strip it and produce a
     * clean task title. Without this, "remind me to buy milk" would strip nothing
     * and produce title "remind me to buy milk".
     */
    private val TASK_PREFIXES = listOf(
        "remind me to", "i need to", "i have to", "i must", "need to",
        "i should", "should", "must", "don't forget to", "dont forget to",
        "remember to", "follow up on", "follow-up on", "follow up with",
        "follow-up with", "check on", "check in on", "review",
        "todo:", "to do:", "task:", "add task", "add:", "action:",
    )

    private fun detectExplicitTaskIntent(lower: String, original: String, reference: LocalDate): ParseResult? {
        val prefix = TASK_PREFIXES.firstOrNull { lower.startsWith(it) } ?: return null
        val stripped = original.drop(prefix.length).trimStart(':', ' ')
        val title = cleanTitle(stripped).ifEmpty { original }
        return ParseResult(
            intent    = ParsedIntent.CREATE_TASK,
            taskTitle = title,
            priority  = extractPriority(lower),
            status    = TaskStatus.OPEN,
            dueDate   = extractDueDate(lower, reference),
        )
    }

    // ── Priority extraction ───────────────────────────────────────────────────

    private fun extractPriority(lower: String): Priority = when {
        lower.contains("urgent") || lower.contains("asap") ||
        lower.contains("critical") || lower.contains("emergency") ||
        lower.contains("immediately") || lower.startsWith("!") -> Priority.URGENT

        lower.contains("important") || lower.contains("high priority") ||
        lower.contains("high-priority") || lower.contains("must do") -> Priority.HIGH

        lower.contains("low priority") || lower.contains("low-priority") ||
        lower.contains("no rush") || lower.contains("whenever") ||
        lower.contains("when you get a chance") -> Priority.LOW

        else -> Priority.MEDIUM
    }

    // ── Due date extraction ───────────────────────────────────────────────────

    private val DAY_NAMES = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY,
    )

    // Matches: "next Monday", "this Friday", "on Wednesday", "on monday", "next fri"
    private val DAY_PATTERN = Regex(
        "\\b(next |this |on )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\\b",
        RegexOption.IGNORE_CASE,
    )

    private fun extractDueDate(lower: String, reference: LocalDate): LocalDate? {
        if (lower.contains("today"))       return reference
        if (lower.contains("tomorrow"))    return reference.plusDays(1)
        if (lower.contains("next week"))   return reference.plusWeeks(1)
        if (lower.contains("next month"))  return reference.plusMonths(1)
        if (lower.contains("end of week") || lower.contains("eow"))
                                           return reference.with(DayOfWeek.FRIDAY)
        if (lower.contains("this week") && !lower.contains("next week"))
                                           return reference.plusDays(1)

        // Day-name resolution: "next Monday", "on Friday", "this Thursday"
        DAY_PATTERN.find(lower)?.let { match ->
            val qualifier = match.groupValues[1].trim().lowercase()
            val dayName   = match.groupValues[2].lowercase()
            val target    = DAY_NAMES[dayName] ?: return@let
            val forceNext = qualifier == "next"
            return nearestWeekday(reference, target, forceNext)
        }

        return null
    }

    /**
     * Returns the nearest future occurrence of [target] on or after [reference].
     * With [forceNext] = true, always returns the occurrence in the next 7 days
     * even if today IS [target].
     */
    private fun nearestWeekday(reference: LocalDate, target: DayOfWeek, forceNext: Boolean): LocalDate {
        var offset = target.value - reference.dayOfWeek.value
        if (offset < 0 || (offset == 0 && forceNext)) offset += 7
        if (offset == 0 && !forceNext) return reference
        return reference.plusDays(offset.toLong())
    }

    // ── Title cleaning ────────────────────────────────────────────────────────

    /**
     * Removes all routing and meta keywords from the raw input to produce a
     * clean task/log title that only contains the actionable content.
     */
    private fun cleanTitle(input: String): String = input
        // Strip explicit task prefixes from the start
        .replace(Regex(
            "^(remind me to|i need to|i have to|i must|need to|i should|should|must" +
            "|don't forget to?|dont forget to?|remember to" +
            "|follow.?up (on|with)|check (on|in on)" +
            "|todo:|to do:|task:|add task|add:|action:" +
            "|note:|log:|logged |win:|fyi:?\\s?|memo:|reminder:" +
            "|urgent:|important:|low priority:?)[:\\s]*",
            RegexOption.IGNORE_CASE,
        ), "")
        // Strip relative date tokens
        .replace(Regex(
            "\\b(today|tomorrow|next week|next month|end of week|eow|this week)\\b",
            RegexOption.IGNORE_CASE,
        ), "")
        // Strip day names (with optional qualifier)
        .replace(Regex(
            "\\b(next |this |on )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\\b",
            RegexOption.IGNORE_CASE,
        ), "")
        // Strip priority keywords
        .replace(Regex(
            "\\b(urgent|asap|critical|important|high priority|high-priority|low priority|low-priority|no rush|whenever)\\b",
            RegexOption.IGNORE_CASE,
        ), "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
        .ifEmpty { input.trim() }
}
