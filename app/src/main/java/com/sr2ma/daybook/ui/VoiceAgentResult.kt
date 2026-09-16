package com.sr2ma.daybook.ui

import com.sr2ma.daybook.domain.ParseResult

/**
 * The result of a voice recognition session, ready for user confirmation.
 *
 * The rule engine ([com.sr2ma.daybook.domain.NaturalLanguageParser]) has already
 * classified the spoken text into an intent. The user sees a confirmation sheet
 * and taps "Add it" to commit, or dismisses to discard.
 */
data class VoiceAgentResult(
    /** Raw text heard by the speech recognizer. */
    val spokenText: String,
    /** What the rule engine decided to do with it. */
    val parseResult: ParseResult,
)
