package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// ── Public API ────────────────────────────────────────────────────────────────

enum class ParsedIntent {
    CREATE_TASK,
    CREATE_LOG,
    CREATE_MEETING,
    CONVERSATION,
    UNKNOWN,
}

data class ParseResult(
    val intent: ParsedIntent,
    // Task fields
    val taskTitle: String? = null,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.OPEN,
    val dueDate: LocalDate? = null,
    val project: String? = null,
    // Log fields
    val logBody: String? = null,
    val logKind: LogKind = LogKind.NOTE,
    // Meeting fields
    val meetingTitle: String? = null,
    val meetingAttendees: List<String> = emptyList(),
    // Conversational agent reply
    val conversationReply: String? = null,
    // Meeting / Task scheduled time
    val meetingTime: LocalTime? = null,
)

/**
 * Pure text-to-intent parser. No I/O, no Android deps, no LLM.
 *
 * Implements the "rule engine first, LLM only for ambiguous remainder" design
 * documented in AGENTS.md §3. This file owns everything above the LLM call:
 *
 *   "remind me to call mom tomorrow"    → CREATE_TASK  (title="call mom", dueDate=+1d)
 *   "meeting with design team at 3pm"  → CREATE_MEETING (attendees=[design team], time=15:00)
 *   "shipped the auth feature today"   → CREATE_LOG WIN
 *   "blocked on review from backend"   → CREATE_LOG BLOCKER
 *   "urgent: fix crash on login"       → CREATE_TASK URGENT priority
 *
 * The LLM is invoked by the caller when this returns UNKNOWN, or when the
 * caller needs field-level enrichment the rule engine cannot supply.
 */
object NaturalLanguageParser {

    // ── Multi-clause splitter ────────────────────────────────────────────────
    /**
     * Splits compound or conversational utterances containing multiple actions or items.
     * Examples:
     * - "Meeting with Acme tomorrow at 2pm, also remind me to buy groceries, and shipped v2 release"
     * - "1. Call Alice tomorrow 2. Review PR 3. Weekly sync on Friday"
     * - "I have tomorrow to maintain one at 9pm for hackathon and then client meeting and at night call my sister"
     */
    fun splitClauses(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return emptyList()

        // 1. Check for explicit line breaks or numbered list markers
        val lineSplit = trimmed.split(Regex("(?m)^\\s*(?:\\d+[\\.\\)]|[-*•])\\s+|(?<=[.!?])\\s+(?=\\d+[\\.\\)])|\\r?\\n+"))
            .map { cleanClause(it) }
            .filter { it.isNotBlank() && it.length > 2 }
        val baseClauses = if (lineSplit.size > 1) lineSplit else listOf(trimmed)

        // 2. Split on compound conjunction markers:
        val clauseRegex = Regex(
            "(?i)\\s*(?:;\\s*|\\band then\\b|\\balso remind me to\\b|\\band remind me to\\b|\\balso\\b|\\bthen\\b|\\bone is for\\b|\\bsecond is for\\b|\\bthird is for\\b|\\banother is for?\\b|\\bnext is\\b|\\bplus\\b)\\s*"
        )
        val stage2Clauses = baseClauses.flatMap { clause ->
            val parts = clause.split(clauseRegex)
                .map { cleanClause(it) }
                .filter { it.isNotBlank() && it.length > 2 }
            if (parts.size > 1) parts else listOf(clause)
        }

        // 3. Sub-clause splitter: split on " and " or ", " followed by action / intent markers
        val andActionRegex = Regex(
            "(?i)\\s+(?:and|,)\\s+(?=(?:(?:(?:a|an|the|one|two|three|four|five|six|seven|eight|nine|ten|\\d+)\\s+)?(?:meeting|call|sync|remind|task|tasks|todo|schedule|log|note|review|submit|buy|fix|finish|finished|clean|draft|send|ship|shipped|completed|blocked|decided|maintain)\\b|(?:(?:at night|in the evening|in the morning|tomorrow|today|day after|you know at night)\\s+)?(?:i have to|i need to|we have to|call|maintain|sync|meeting)\\b))"
        )
        val stage3Clauses = stage2Clauses.flatMap { clause ->
            val parts = clause.split(andActionRegex)
                .map { cleanClause(it) }
                .filter { it.isNotBlank() && it.length > 2 }
            if (parts.size > 1) parts else listOf(clause)
        }

        return stage3Clauses.map { cleanClause(it) }.filter { it.isNotBlank() && it.length > 2 }
    }

    private fun cleanClause(clause: String): String {
        return clause.trim()
            .replace(Regex("^(?:and\\s+you\\s+know\\s+|you\\s+know\\s+|and\\s+|also\\s+|then\\s+|,\\s*|\\.\\s*|one\\s+is\\s+for\\s+|second\\s+is\\s+for\\s+|third\\s+is\\s+for\\s+|another\\s+is\\s+(?:for\\s+)?|there\\s+is\\s+for\\s+|for\\s+|are\\s+you\\s+able\\s+to\\s+save\\s+|can\\s+you\\s+save\\s+|save\\s+)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?:,\\s*right|\\s+right|\\s+please|\\s+okay)$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    /**
     * Parses multiple items from a compound utterance offline.
     */
    fun parseMulti(input: String, referenceDate: LocalDate = LocalDate.now()): List<ParseResult> {
        val clauses = splitClauses(input)
        if (clauses.isEmpty()) return listOf(ParseResult(intent = ParsedIntent.UNKNOWN))
        return clauses.map { parse(it, referenceDate) }
    }

    // ── Public entry point ────────────────────────────────────────────────────

    fun parse(input: String, referenceDate: LocalDate = LocalDate.now()): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ParseResult(intent = ParsedIntent.UNKNOWN)
        val lower = trimmed.lowercase()

        // Order matters: most-specific patterns first to avoid false positives.
        detectConversationalIntent(lower, trimmed)?.let { return it }
        detectMeetingIntent(lower, trimmed, referenceDate)?.let { return it }
        detectLogIntent(lower, trimmed)?.let { return it }
        detectExplicitTaskIntent(lower, trimmed, referenceDate)?.let { return it }

        // Conversational questions (e.g. "what is my schedule?", "can you help?") should not become tasks
        if (isPureQuestion(lower)) {
            return ParseResult(intent = ParsedIntent.UNKNOWN)
        }

        // Default: anything else is a task.
        return ParseResult(
            intent      = ParsedIntent.CREATE_TASK,
            taskTitle   = cleanTitle(trimmed),
            priority    = extractPriority(lower),
            status      = TaskStatus.OPEN,
            dueDate     = extractDueDate(lower, referenceDate),
            project     = extractProject(trimmed),
            meetingTime = extractTime(lower),
        )
    }

    // ── Conversational intent detection ───────────────────────────────────────

    private val GREETING_REGEX = Regex(
        "^(hi|hello|hey|howdy|good\\s+(morning|afternoon|evening|day))\\b[\\s!,.]*$",
        RegexOption.IGNORE_CASE,
    )

    private fun detectConversationalIntent(lower: String, original: String): ParseResult? {
        val clean = lower.trimEnd('?', '!', '.', ' ')
        if (GREETING_REGEX.matches(clean)) {
            return ParseResult(
                intent = ParsedIntent.CONVERSATION,
                conversationReply = "Hello! How can I help you today? You can ask me to schedule meetings, add tasks, or record daily logs.",
            )
        }
        if (clean.contains("how are you") || clean.contains("how're you") || clean.contains("how are u") || clean == "what's up" || clean == "whats up") {
            return ParseResult(
                intent = ParsedIntent.CONVERSATION,
                conversationReply = "I'm doing well, thank you! Ready to help you organize your tasks, meetings, and notes.",
            )
        }
        if (clean.contains("who are you") || clean.contains("what is your name")) {
            return ParseResult(
                intent = ParsedIntent.CONVERSATION,
                conversationReply = "I am Daybook's intelligent on-device assistant. I help you track tasks, schedule meetings, write logs, and manage passes.",
            )
        }
        if (clean.contains("what can you do") || clean.contains("what are you capable of") || clean == "help") {
            return ParseResult(
                intent = ParsedIntent.CONVERSATION,
                conversationReply = "You can talk or type to me to add tasks, schedule meetings, record work logs, and import passes. You can also give me multiple items in a single sentence!",
            )
        }
        if (clean.startsWith("thanks") || clean.startsWith("thank you")) {
            return ParseResult(
                intent = ParsedIntent.CONVERSATION,
                conversationReply = "You're very welcome! Let me know whenever you need anything else.",
            )
        }
        return null
    }

    private fun isPureQuestion(lower: String): Boolean {
        if (!lower.endsWith("?")) return false
        val questionStarters = listOf("what is", "what are", "how do", "can you", "could you", "tell me", "where is", "why is", "who is")
        val hasTaskVerb = listOf("remind", "call", "schedule", "create", "add", "buy", "send", "review", "fix", "write").any { lower.contains(it) }
        return questionStarters.any { lower.startsWith(it) } && !hasTaskVerb
    }

    // ── Time extraction ──────────────────────────────────────────────────────

    private val TIME_12H_REGEX = Regex(
        "(?:at|@)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val OCLOCK_REGEX = Regex(
        "(?:at|@)\\s*(\\d{1,2})\\s*o'?clock(?:\\s*(?:at|in the)?\\s*(morning|afternoon|evening|night))?",
        RegexOption.IGNORE_CASE,
    )
    private val TIME_24H_REGEX = Regex(
        "(?:at|@)\\s*(\\d{1,2}):(\\d{2})\\b",
        RegexOption.IGNORE_CASE,
    )
    private val RELATIVE_TIME_REGEX = Regex(
        "\\b(at night|in the evening|in the morning|in the afternoon)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun extractTime(lower: String): LocalTime? {
        TIME_12H_REGEX.find(lower)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            val amPm = match.groupValues[3].lowercase()
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            return try { LocalTime.of(hour, minute) } catch (_: Exception) { null }
        }
        OCLOCK_REGEX.find(lower)?.let { match ->
            var hour = match.groupValues[1].toIntOrNull() ?: return@let
            val period = match.groupValues[2].lowercase()
            if ((period == "evening" || period == "night" || period == "afternoon") && hour < 12) hour += 12
            return try { LocalTime.of(hour, 0) } catch (_: Exception) { null }
        }
        TIME_24H_REGEX.find(lower)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            return try { LocalTime.of(hour, minute) } catch (_: Exception) { null }
        }
        RELATIVE_TIME_REGEX.find(lower)?.let { match ->
            return when (match.groupValues[1].lowercase()) {
                "in the morning" -> LocalTime.of(9, 0)
                "in the afternoon" -> LocalTime.of(14, 0)
                "in the evening" -> LocalTime.of(18, 0)
                "at night" -> LocalTime.of(20, 0)
                else -> null
            }
        }
        return null
    }

    // ── Meeting detection ─────────────────────────────────────────────────────

    /**
     * Patterns that signal a meeting/call intent.
     * Ordered from most specific to least specific.
     */
    private val MEETING_PREFIXES = listOf(
        // "meeting with X", "call with X", "zoom with X", etc.
        Regex("(?:^|\\b)(meeting|call|zoom|video call|video chat|catch.?up|1:1|one.on.one|sync|stand.?up|check.?in|debrief|retro|retrospective|interview)\\s+with\\b", RegexOption.IGNORE_CASE),
        Regex("(?:^|\\b)(meeting|sync):", RegexOption.IGNORE_CASE),
        // "schedule / book / set up a meeting with X"
        Regex("(?:^|\\b)(schedule|book|set up|arrange|organise|organize|plan)\\s+(?:a\\s+|an\\s+)?(meeting|call|zoom|video|catch.?up|1:1|sync|stand.?up)\\b", RegexOption.IGNORE_CASE),
        // "have / join / attend a meeting"
        Regex("(?:^|\\b)(have|join|attend)\\s+(?:a\\s+|an\\s+)?(meeting|call|zoom|stand.?up)\\b", RegexOption.IGNORE_CASE),
        // "call mom", "call client", "call team" — bare call + noun (no "to" after)
        Regex("^call\\s+[a-z](?!.*\\bto\\b)", RegexOption.IGNORE_CASE),
        Regex("\\bcall\\s+(?:my\\s+)?(?:sister|brother|mom|dad|mother|father|client|doctor)\\b", RegexOption.IGNORE_CASE),
        // "Weekly / Daily / Engineering sync on Friday" or "sync with team"
        Regex("(?:^|\\b)(weekly|daily|monthly|team|engineering|product|design|client|sprint|standup)?\\s*(sync|meeting|1:1|standup)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(sync|1:1|standup|call|meeting)\\s+(with|on|at)\\b", RegexOption.IGNORE_CASE),
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
            // Grab everything after "with" and stop at "tomorrow / today / day after tomorrow / at / on / @" (time/day marker) or end
            original.substring(withIdx + 6)
                .split(Regex("\\btomorrow\\b|\\btoday\\b|\\bday after tomorrow\\b|\\bat\\b|\\bon\\b|@|,", RegexOption.IGNORE_CASE))
                .first()
                .split(",")
                .map { it.trim() }
                .filter { it.length > 1 }
        } else emptyList()

        val time = extractTime(lower)
        val dueDate = extractDueDate(lower, reference)

        // Clean up meeting title: strip prefixes, dates, times, and "with"
        var clean = original
            .replace(Regex("^(?:\\[sample\\]\\s*|meeting:?\\s*|call:?\\s*|sync:?\\s*|there is for\\s+|some\\s+)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(today|tomorrow|day after tomorrow|next week|next month|this week)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(next |this |on )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?:at|@)\\s*\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?:at|@)\\s*\\d{1,2}\\s*o'?clock(?:\\s*(?:at|in the)?\\s*(?:morning|afternoon|evening|night))?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(at night|in the evening|in the morning|in the afternoon)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        if (clean.startsWith("with ", ignoreCase = true)) {
            clean = clean.removePrefix("with ").removePrefix("With ").trim()
        }

        val finalTitle = when {
            clean.isNotBlank() && !clean.equals("meeting", ignoreCase = true) -> clean
            attendees.isNotEmpty() -> "Meeting with ${attendees.joinToString(", ")}"
            else -> "Meeting"
        }

        return ParseResult(
            intent           = ParsedIntent.CREATE_MEETING,
            meetingTitle     = finalTitle,
            meetingAttendees = attendees,
            dueDate          = dueDate,
            meetingTime      = time,
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
            project   = extractProject(original),
        )
    }

    private val SHORTHAND_URGENT = Regex("(?:^|\\s)!urgent\\b", RegexOption.IGNORE_CASE)
    private val SHORTHAND_HIGH   = Regex("(?:^|\\s)!high\\b", RegexOption.IGNORE_CASE)
    private val SHORTHAND_LOW    = Regex("(?:^|\\s)!low\\b", RegexOption.IGNORE_CASE)
    private val SHORTHAND_MED    = Regex("(?:^|\\s)!(med|medium)\\b", RegexOption.IGNORE_CASE)

    // ── Priority extraction ───────────────────────────────────────────────────

    private fun extractPriority(lower: String): Priority = when {
        SHORTHAND_URGENT.containsMatchIn(lower) -> Priority.URGENT
        SHORTHAND_HIGH.containsMatchIn(lower) -> Priority.HIGH
        SHORTHAND_LOW.containsMatchIn(lower) -> Priority.LOW
        SHORTHAND_MED.containsMatchIn(lower) -> Priority.MEDIUM

        lower.contains("urgent") || lower.contains("asap") ||
        lower.contains("critical") || lower.contains("emergency") ||
        lower.contains("immediately") || Regex("(?:^|\\s)!(?:\\s|$)").containsMatchIn(lower) -> Priority.URGENT

        lower.contains("important") || lower.contains("high priority") ||
        lower.contains("high-priority") || lower.contains("must do") -> Priority.HIGH

        lower.contains("low priority") || lower.contains("low-priority") ||
        lower.contains("no rush") || lower.contains("whenever") ||
        lower.contains("when you get a chance") -> Priority.LOW

        else -> Priority.MEDIUM
    }

    // ── Project extraction ────────────────────────────────────────────────────

    private val PROJECT_TAG_REGEX = Regex("(?:^|\\s)#([\\p{L}][\\p{L}0-9_-]*)")

    private fun extractProject(input: String): String? {
        return PROJECT_TAG_REGEX.find(input)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
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
        if (lower.contains("day after tomorrow")) return reference.plusDays(2)
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
        // Strip conversational lead-ins or command prefixes
        .replace(Regex(
            "^(can you\\s+|could you\\s+|please\\s+|are you able to\\s+|help me\\s+|save\\s+|schedule\\s+|add\\s+|create\\s+|there is for\\s+|some\\s+)+",
            RegexOption.IGNORE_CASE,
        ), "")
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
        // Strip relative date tokens (checking 'day after tomorrow' first)
        .replace(Regex(
            "\\b(day after tomorrow|today|tomorrow|next week|next month|end of week|eow|this week)\\b",
            RegexOption.IGNORE_CASE,
        ), "")
        // Strip day names (with optional qualifier)
        .replace(Regex(
            "\\b(next |this |on )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\\b",
            RegexOption.IGNORE_CASE,
        ), "")
        // Strip time expressions
        .replace(Regex("(?:at|@)\\s*\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("(?:at|@)\\s*\\d{1,2}\\s*o'?clock(?:\\s*(?:at|in the)?\\s*(?:morning|afternoon|evening|night))?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(at night|in the evening|in the morning|in the afternoon)\\b", RegexOption.IGNORE_CASE), "")
        // Strip priority keywords and shorthand tags
        .replace(Regex("(?:^|\\s)!(urgent|high|medium|med|low)\\b", RegexOption.IGNORE_CASE), " ")
        // Strip project tags (#project) — requires starting with letter to avoid stripping issue numbers like #123
        .replace(Regex("(?:^|\\s)#[\\p{L}][\\p{L}0-9_-]*(?=\\s|$)"), " ")
        .replace(Regex(
            "\\b(urgent|asap|critical|important|high priority|high-priority|low priority|low-priority|no rush|whenever)\\b",
            RegexOption.IGNORE_CASE,
        ), "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
        .ifEmpty { input.trim() }
}
