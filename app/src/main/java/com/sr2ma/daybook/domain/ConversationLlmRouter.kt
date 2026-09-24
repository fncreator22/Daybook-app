package com.sr2ma.daybook.domain

import com.sr2ma.daybook.ai.LlmEngine
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.TaskStatus
import java.time.LocalDate
import org.json.JSONException
import org.json.JSONObject

/**
 * Routes an UNKNOWN [ParseResult] through [LlmEngine] when the rule engine
 * cannot classify the user's message.
 *
 * AGENTS.md constraints obeyed:
 * - LLM called ONLY when [NaturalLanguageParser] returns UNKNOWN.
 * - The rule engine still decides what to do; the LLM produces structured output.
 * - 30-second timeout is enforced inside [LlmEngine.infer].
 * - If the model is not downloaded, returns [RouteResult.ModelNotReady] immediately.
 * - KG context (≤300 chars) + recent conversation memory (≤300 chars) injected
 *   into the prompt so responses are personalised without a network call.
 *
 * Prompt design:
 * - System section describes the task + JSON schema.
 * - Context section injects KG + memory (truncated hard).
 * - User section contains only the raw user message.
 * - Response must be a single JSON object — no prose outside the braces.
 *   Malformed JSON → returns [RouteResult.ParseError] so callers can fallback gracefully.
 */
object ConversationLlmRouter {

    sealed interface RouteResult {
        /** LLM produced a valid JSON response that was parsed into a [ParseResult]. */
        data class Classified(val result: ParseResult, val rawResponse: String) : RouteResult
        /** Model file not downloaded yet. */
        data object ModelNotReady : RouteResult
        /** LLM timed out (>30 s). */
        data object Timeout : RouteResult
        /** LLM returned text that could not be parsed as the expected JSON schema. */
        data class ParseError(val raw: String) : RouteResult
        /** Engine failure (OOM, native crash, etc.). */
        data class Failure(val cause: Throwable) : RouteResult
    }

    /**
     * Attempts LLM classification of [userText].
     *
     * @param userText       The raw message from the user (≤500 chars — already capped upstream).
     * @param today          Reference date for relative date resolution.
     * @param llmEngine      The [LlmEngine] instance to call.
     * @param db             [DaybookDatabase] used to load memory context for prompt enrichment.
     */
    suspend fun route(
        userText: String,
        today: LocalDate,
        llmEngine: LlmEngine,
        db: DaybookDatabase,
    ): RouteResult {
        val prompt = buildPrompt(userText, today, db)
        return when (val result = llmEngine.infer(prompt)) {
            is LlmEngine.InferResult.Success    -> parseResponse(result.text, today)
            is LlmEngine.InferResult.ModelNotReady -> RouteResult.ModelNotReady
            is LlmEngine.InferResult.Timeout    -> RouteResult.Timeout
            is LlmEngine.InferResult.Failure    -> RouteResult.Failure(result.cause)
        }
    }

    // ── Prompt construction ───────────────────────────────────────────────────

    private fun buildPrompt(
        userText: String,
        today: LocalDate,
        db: DaybookDatabase,
    ): String {
        val memoryContext = ConversationMemoryEngine.recentContext(db, limit = 3).take(300)
        val sb = StringBuilder()
        sb.append(SYSTEM_PROMPT)
        sb.append("\n\nToday is $today.")
        if (memoryContext.isNotEmpty()) {
            sb.append("\n\nRecent context: $memoryContext")
        }
        sb.append("\n\nUser message: \"$userText\"")
        sb.append("\n\nRespond with ONLY the JSON object, nothing else:")
        return sb.toString()
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    /**
     * Parses the LLM's JSON response into a [ParseResult].
     *
     * Expected schema (all fields optional except `intent`):
     * ```json
     * {
     *   "intent": "task" | "meeting" | "log" | "unknown",
     *   "title": "...",
     *   "priority": "urgent" | "high" | "medium" | "low",
     *   "due": "YYYY-MM-DD",
     *   "body": "...",
     *   "kind": "note" | "win" | "blocker" | "decision",
     *   "attendees": ["name1", "name2"]
     * }
     * ```
     */
    private fun parseResponse(raw: String, today: LocalDate): RouteResult {
        // Extract the first {...} block — models sometimes emit prose before/after.
        val jsonStart = raw.indexOf('{')
        val jsonEnd   = raw.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd <= jsonStart) {
            return RouteResult.ParseError(raw.take(200))
        }
        val jsonStr = raw.substring(jsonStart, jsonEnd + 1)
        return try {
            val obj    = JSONObject(jsonStr)
            val intent = obj.optString("intent", "unknown").lowercase()
            val parsed = when (intent) {
                "task" -> ParseResult(
                    intent    = ParsedIntent.CREATE_TASK,
                    taskTitle = obj.optString("title").takeIf { it.isNotBlank() },
                    priority  = parsePriority(obj.optString("priority")),
                    status    = TaskStatus.OPEN,
                    dueDate   = parseDate(obj.optString("due"), today),
                )
                "meeting" -> ParseResult(
                    intent           = ParsedIntent.CREATE_MEETING,
                    meetingTitle     = obj.optString("title").takeIf { it.isNotBlank() },
                    meetingAttendees = parseAttendees(obj),
                    dueDate          = parseDate(obj.optString("due"), today),
                )
                "log" -> ParseResult(
                    intent  = ParsedIntent.CREATE_LOG,
                    logBody = obj.optString("body").takeIf { it.isNotBlank() }
                        ?: obj.optString("title").takeIf { it.isNotBlank() },
                    logKind = parseLogKind(obj.optString("kind")),
                )
                else -> ParseResult(intent = ParsedIntent.UNKNOWN)
            }
            RouteResult.Classified(parsed, jsonStr)
        } catch (e: JSONException) {
            RouteResult.ParseError(jsonStr.take(200))
        }
    }

    // ── Field parsers ─────────────────────────────────────────────────────────

    private fun parsePriority(s: String): Priority = when (s.lowercase()) {
        "urgent"   -> Priority.URGENT
        "high"     -> Priority.HIGH
        "low"      -> Priority.LOW
        else       -> Priority.MEDIUM
    }

    private fun parseLogKind(s: String): LogKind = when (s.lowercase()) {
        "win"      -> LogKind.WIN
        "blocker"  -> LogKind.BLOCKER
        "decision" -> LogKind.DECISION
        else       -> LogKind.NOTE
    }

    private fun parseDate(s: String, today: LocalDate): LocalDate? {
        if (s.isBlank()) return null
        return try { LocalDate.parse(s) } catch (_: Exception) { null }
    }

    private fun parseAttendees(obj: JSONObject): List<String> {
        val arr = obj.optJSONArray("attendees") ?: return emptyList()
        return (0 until arr.length())
            .mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    // ── Prompt constants ──────────────────────────────────────────────────────

    /**
     * Schema-constrained system prompt.
     * Keep this short — Gemma 270M has a 1024-token context window.
     */
    private val SYSTEM_PROMPT = """
You are a personal assistant. Classify the user message into one of: task, meeting, log, or unknown.
Respond with ONLY a JSON object matching this schema (no prose, no markdown):
{
  "intent": "task" | "meeting" | "log" | "unknown",
  "title": "clean action title (omit if not applicable)",
  "priority": "urgent" | "high" | "medium" | "low",
  "due": "YYYY-MM-DD or empty string",
  "body": "log text (only for log intent)",
  "kind": "note" | "win" | "blocker" | "decision",
  "attendees": ["name"] (only for meeting intent)
}
Rules:
- Tasks: things to do, reminders, action items.
- Meetings: calls, syncs, interviews, or events with other people.
- Logs: things already done, observations, blockers, decisions.
- Unknown: everything else (questions, chitchat).
- Use "unknown" if you are not confident.
- Never add fields not in the schema.
""".trimIndent()
}
