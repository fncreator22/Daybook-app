package com.sr2ma.daybook.domain

import com.sr2ma.daybook.ai.LlmEngine
import com.sr2ma.daybook.data.DaybookDatabase
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime
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
        /** LLM produced a valid JSON response that was parsed into [ParseResult]s. */
        data class Classified(
            val results: List<ParseResult>,
            val chatResponse: String?,
            val rawResponse: String,
        ) : RouteResult {
            constructor(result: ParseResult, rawResponse: String) : this(listOf(result), null, rawResponse)
            val result: ParseResult get() = results.firstOrNull() ?: ParseResult(ParsedIntent.UNKNOWN)
        }
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
     * @param db             [DaybookDatabase] used to load memory context and OKF knowledge graph for prompt enrichment.
     */
    suspend fun route(
        userText: String,
        today: LocalDate,
        llmEngine: LlmEngine,
        db: DaybookDatabase,
    ): RouteResult {
        val prompt = buildPrompt(userText, today, db)
        val firstResult = when (val r = llmEngine.infer(prompt)) {
            is LlmEngine.InferResult.Success       -> parseResponse(r.text, today)
            is LlmEngine.InferResult.ModelNotReady -> return RouteResult.ModelNotReady
            is LlmEngine.InferResult.Timeout       -> return RouteResult.Timeout
            is LlmEngine.InferResult.Failure       -> return RouteResult.Failure(r.cause)
        }

        // If parsing succeeded, return immediately.
        if (firstResult is RouteResult.Classified) return firstResult

        // ── Hallucination guard: one retry with a minimal prompt ──────────────
        val retryPrompt = buildRetryPrompt(userText, today)
        return when (val r2 = llmEngine.infer(retryPrompt)) {
            is LlmEngine.InferResult.Success       -> parseResponse(r2.text, today)
            is LlmEngine.InferResult.ModelNotReady -> RouteResult.ModelNotReady
            is LlmEngine.InferResult.Timeout       -> RouteResult.Timeout
            is LlmEngine.InferResult.Failure       -> RouteResult.Failure(r2.cause)
        }
    }

    // ── Prompt construction ───────────────────────────────────────────────────

    private fun buildPrompt(
        userText: String,
        today: LocalDate,
        db: DaybookDatabase,
    ): String {
        val memoryContext = ConversationMemoryEngine.recentContext(db, limit = 3).take(300)
        val kgNodes = KnowledgeGraphEngine.expandFromText(db, userText)
        val kgContext = KnowledgeGraphEngine.toOkfTriples(db, kgNodes).take(300)

        val sb = StringBuilder()
        sb.append(SYSTEM_PROMPT)
        sb.append("\n\nToday is $today.")
        if (kgContext.isNotEmpty()) {
            sb.append("\n\n$kgContext")
        }
        if (memoryContext.isNotEmpty()) {
            sb.append("\n\nRecent conversation: $memoryContext")
        }
        sb.append("\n\nUser message: \"$userText\"")
        sb.append("\n\nRespond with ONLY the JSON object, nothing else:")
        return sb.toString()
    }

    /**
     * Minimal retry prompt — no context, just message + ultra-strict instruction.
     * Used when [buildPrompt] response couldn't be parsed as JSON.
     */
    private fun buildRetryPrompt(userText: String, today: LocalDate): String =
        "Today: $today. Message: \"$userText\"\n" +
        "Output ONLY this JSON (no other text): {\"chatResponse\":\"...\",\"actions\":[{\"intent\":\"task\"|\"meeting\"|\"log\"|\"unknown\",\"title\":\"...\",\"due\":\"YYYY-MM-DD\"}]}"

    // ── Response parsing ──────────────────────────────────────────────────────

    private fun parseResponse(raw: String, today: LocalDate): RouteResult {
        val jsonStart = raw.indexOf('{')
        val jsonEnd   = raw.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd <= jsonStart) {
            return RouteResult.ParseError(raw.take(200))
        }
        val jsonStr = raw.substring(jsonStart, jsonEnd + 1)
        return try {
            val obj = JSONObject(jsonStr)
            val chatResponse = obj.optString("chatResponse").takeIf { it.isNotBlank() }

            val actionsArr = obj.optJSONArray("actions")
            val results = if (actionsArr != null && actionsArr.length() > 0) {
                (0 until actionsArr.length()).mapNotNull { i ->
                    parseEntityObject(actionsArr.getJSONObject(i), today)
                }
            } else {
                listOfNotNull(parseEntityObject(obj, today))
            }

            if (results.isEmpty() || (results.size == 1 && results[0].intent == ParsedIntent.UNKNOWN && chatResponse != null)) {
                if (chatResponse != null) {
                    RouteResult.Classified(listOf(ParseResult(ParsedIntent.CONVERSATION, conversationReply = chatResponse)), chatResponse, jsonStr)
                } else {
                    RouteResult.Classified(listOf(ParseResult(ParsedIntent.UNKNOWN)), chatResponse, jsonStr)
                }
            } else {
                RouteResult.Classified(results, chatResponse, jsonStr)
            }
        } catch (e: JSONException) {
            RouteResult.ParseError(jsonStr.take(200))
        }
    }

    private fun parseEntityObject(obj: JSONObject, today: LocalDate): ParseResult {
        val intent = obj.optString("intent", "unknown").lowercase()
        return when (intent) {
            "task" -> ParseResult(
                intent      = ParsedIntent.CREATE_TASK,
                taskTitle   = obj.optString("title").takeIf { it.isNotBlank() },
                priority    = parsePriority(obj.optString("priority")),
                status      = TaskStatus.OPEN,
                dueDate     = parseDate(obj.optString("due"), today),
                meetingTime = parseTime(obj.optString("time")),
            )
            "meeting" -> ParseResult(
                intent           = ParsedIntent.CREATE_MEETING,
                meetingTitle     = obj.optString("title").takeIf { it.isNotBlank() },
                meetingAttendees = parseAttendees(obj),
                dueDate          = parseDate(obj.optString("due"), today),
                meetingTime      = parseTime(obj.optString("time")),
            )
            "log" -> ParseResult(
                intent  = ParsedIntent.CREATE_LOG,
                logBody = obj.optString("body").takeIf { it.isNotBlank() }
                    ?: obj.optString("title").takeIf { it.isNotBlank() },
                logKind = parseLogKind(obj.optString("kind")),
            )
            "conversation", "chitchat" -> ParseResult(
                intent            = ParsedIntent.CONVERSATION,
                conversationReply = obj.optString("chatResponse").takeIf { it.isNotBlank() }
                    ?: obj.optString("title").takeIf { it.isNotBlank() },
            )
            else -> ParseResult(intent = ParsedIntent.UNKNOWN)
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

    private fun parseTime(s: String): LocalTime? {
        if (s.isBlank()) return null
        val trimmed = s.trim()
        return try {
            LocalTime.parse(trimmed)
        } catch (_: Exception) {
            val parts = trimmed.split(":")
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                if (h != null && m != null) {
                    try { LocalTime.of(h, m) } catch (_: Exception) { null }
                } else null
            } else null
        }
    }

    private fun parseAttendees(obj: JSONObject): List<String> {
        val arr = obj.optJSONArray("attendees") ?: return emptyList()
        return (0 until arr.length())
            .mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    // ── Prompt constants ──────────────────────────────────────────────────────

    private val SYSTEM_PROMPT = """
You are an intelligent personal productivity assistant for Daybook. Classify user input into structured tasks, meetings, logs, or chitchat.
Respond with ONLY a JSON object matching this schema (no prose outside JSON):
{
  "chatResponse": "Conversational reply summarizing what was understood or replying naturally",
  "actions": [
    {
      "intent": "task" | "meeting" | "log" | "unknown",
      "title": "clean action title",
      "priority": "urgent" | "high" | "medium" | "low",
      "due": "YYYY-MM-DD or empty",
      "time": "HH:MM (e.g. 15:00) or empty",
      "body": "log text (only for log intent)",
      "kind": "note" | "win" | "blocker" | "decision",
      "attendees": ["name"]
    }
  ]
}
Rules:
- Tasks: things to do, reminders, action items.
- Meetings: calls, syncs, interviews, or events with people.
- Logs: completed work, observations, blockers, decisions.
- If user input mentions multiple actions, return each in the actions list.
- Keep chatResponse helpful, friendly, and natural.
""".trimIndent()
}
