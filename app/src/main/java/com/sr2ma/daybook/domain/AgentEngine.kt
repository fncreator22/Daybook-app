package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.SuggestionType
import com.sr2ma.daybook.domain.model.Task
import java.time.LocalDate

/**
 * A proactive suggestion produced by [AgentEngine].
 *
 * Displayed as a dismissible card on the Today screen. Never mutates data.
 * [relatedEntityType] and [relatedEntityId] identify the entity the user should act on.
 */
data class AgentSuggestion(
    val type: SuggestionType,
    val body: String,
    val relatedEntityType: String? = null,
    val relatedEntityId: Long? = null,
)

/**
 * Computes proactive suggestions from the current data snapshot.
 *
 * Pure: takes lists in, returns suggestions out. No I/O, no Android, no LLM.
 *
 * Current suggestion types:
 * - [SuggestionType.STALE_TASK] — open task not updated in >7 days (ADR-0001)
 * - [SuggestionType.FOLLOW_UP_DUE] — meeting with an outstanding follow-up date (ADR-0001)
 *
 * The LLM may later enrich suggestion bodies with natural language, but the
 * decision of WHICH suggestions to show is always made by this rule engine.
 */
object AgentEngine {

    fun computeSuggestions(
        tasks: List<Task>,
        meetings: List<Meeting>,
        today: LocalDate,
    ): List<AgentSuggestion> {
        val suggestions = mutableListOf<AgentSuggestion>()

        // ── STALE_TASK ─────────────────────────────────────────────────────
        tasks.filter { CadenceEngine.isStale(it, today) }.forEach { task ->
            suggestions += AgentSuggestion(
                type = SuggestionType.STALE_TASK,
                body = "\"${task.title}\" hasn't been updated in a while. Still relevant?",
                relatedEntityType = "task",
                relatedEntityId = task.id,
            )
        }

        // ── FOLLOW_UP_DUE ──────────────────────────────────────────────────
        meetings.filter { it.needsFollowUpBy(today) }.forEach { meeting ->
            suggestions += AgentSuggestion(
                type = SuggestionType.FOLLOW_UP_DUE,
                body = "Follow up due for \"${meeting.title}\".",
                relatedEntityType = "meeting",
                relatedEntityId = meeting.id,
            )
        }

        return suggestions
    }
}
