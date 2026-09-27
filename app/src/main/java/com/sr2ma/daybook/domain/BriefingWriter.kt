package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.LogKind

/**
 * Produces a human-readable daily briefing from a [TodayBoard].
 *
 * Pure: string in, string out. No Android, no I/O, no LLM.
 *
 * The briefing is designed to be read aloud via TextToSpeech or displayed as
 * a notification summary. Keep each section short and scannable.
 */
object BriefingWriter {

    fun generate(board: TodayBoard): String = write(board)

    fun write(board: TodayBoard): String {
        if (board.isEmpty && board.completedToday == 0) {
            return "Your day is clear. Nothing on your agenda, no meetings, no follow-ups."
        }

        val parts = mutableListOf<String>()

        // ── Wins / completions ────────────────────────────────────────────
        if (board.completedToday > 0) {
            parts += "You completed ${board.completedToday} task${if (board.completedToday == 1) "" else "s"} today."
        }

        // ── Overdue ───────────────────────────────────────────────────────
        if (board.overdue.isNotEmpty()) {
            val count = board.overdue.size
            val label = if (count == 1) "1 overdue task" else "$count overdue tasks"
            val titles = board.overdue.take(3).joinToString(", ") { it.title }
            parts += "$label: $titles${if (count > 3) " and ${count - 3} more" else ""}."
        }

        // ── Due today ─────────────────────────────────────────────────────
        if (board.dueToday.isNotEmpty()) {
            val count = board.dueToday.size
            val titles = board.dueToday.take(3).joinToString(", ") { it.title }
            parts += "${count} due today: $titles${if (count > 3) " and more" else ""}."
        }

        // ── In progress ───────────────────────────────────────────────────
        if (board.inProgress.isNotEmpty()) {
            val titles = board.inProgress.take(2).joinToString(", ") { it.title }
            parts += "In progress: $titles."
        }

        // ── Meetings ──────────────────────────────────────────────────────
        if (board.meetings.isNotEmpty()) {
            val count = board.meetings.size
            val label = if (count == 1) "1 meeting" else "$count meetings"
            val titles = board.meetings.take(2).joinToString(", ") { it.title }
            parts += "$label: $titles."
        }

        // ── Follow-ups ────────────────────────────────────────────────────
        if (board.followUps.isNotEmpty()) {
            val count = board.followUps.size
            parts += "${count} follow-up${if (count == 1) "" else "s"} outstanding."
        }

        // ── Blockers from today's log ─────────────────────────────────────
        val blockers = board.log.filter { it.kind == LogKind.BLOCKER }
        if (blockers.isNotEmpty()) {
            val titles = blockers.take(2).joinToString(", ") { it.body }
            parts += "Blockers: $titles."
        }

        return parts.joinToString(" ")
    }
}
