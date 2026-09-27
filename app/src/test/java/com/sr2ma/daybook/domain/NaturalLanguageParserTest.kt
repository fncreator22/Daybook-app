package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for NaturalLanguageParser — pure input→output, no I/O.
 *
 * Each test follows the "arrange one input, assert one output" pattern used
 * across the existing domain test suite.
 */
class NaturalLanguageParserTest {

    // ── Task capture ──────────────────────────────────────────────────────────

    @Test
    fun `bare text becomes a task with OPEN status and inferred MEDIUM priority`() {
        val result = NaturalLanguageParser.parse("send the report to Alice")

        assertEquals(ParsedIntent.CREATE_TASK, result.intent)
        assertEquals("send the report to Alice", result.taskTitle)
        assertEquals(Priority.MEDIUM, result.priority)
        assertEquals(TaskStatus.OPEN, result.status)
        assertNull(result.dueDate)
    }

    @Test
    fun `urgent keyword bumps priority to URGENT`() {
        val result = NaturalLanguageParser.parse("urgent: fix the login bug")

        assertEquals(ParsedIntent.CREATE_TASK, result.intent)
        assertEquals(Priority.URGENT, result.priority)
    }

    @Test
    fun `important keyword maps to HIGH priority`() {
        val result = NaturalLanguageParser.parse("important review the contract")

        assertEquals(Priority.HIGH, result.priority)
    }

    @Test
    fun `low priority keyword maps to LOW`() {
        val result = NaturalLanguageParser.parse("low priority: clean up old emails")

        assertEquals(Priority.LOW, result.priority)
    }

    @Test
    fun `today keyword sets due date to today`() {
        val today = java.time.LocalDate.of(2026, 9, 11)
        val result = NaturalLanguageParser.parse("finish the deck today", referenceDate = today)

        assertEquals(today, result.dueDate)
    }

    @Test
    fun `tomorrow keyword sets due date to next day`() {
        val today = java.time.LocalDate.of(2026, 9, 11)
        val result = NaturalLanguageParser.parse("call James tomorrow", referenceDate = today)

        assertEquals(today.plusDays(1), result.dueDate)
    }

    @Test
    fun `next week keyword sets due date 7 days out`() {
        val today = java.time.LocalDate.of(2026, 9, 11)
        val result = NaturalLanguageParser.parse("submit invoice next week", referenceDate = today)

        assertEquals(today.plusDays(7), result.dueDate)
    }

    // ── Log entry capture ─────────────────────────────────────────────────────

    @Test
    fun `note prefix routes to CREATE_LOG with NOTE kind`() {
        val result = NaturalLanguageParser.parse("note: discussed pricing with the team")

        assertEquals(ParsedIntent.CREATE_LOG, result.intent)
        assertEquals(LogKind.NOTE, result.logKind)
        assertEquals("discussed pricing with the team", result.logBody)
    }

    @Test
    fun `decided keyword routes to CREATE_LOG with DECISION kind`() {
        val result = NaturalLanguageParser.parse("decided to go with option B")

        assertEquals(ParsedIntent.CREATE_LOG, result.intent)
        assertEquals(LogKind.DECISION, result.logKind)
    }

    @Test
    fun `blocked on prefix routes to CREATE_LOG with BLOCKER kind`() {
        val result = NaturalLanguageParser.parse("blocked on waiting for legal sign-off")

        assertEquals(ParsedIntent.CREATE_LOG, result.intent)
        assertEquals(LogKind.BLOCKER, result.logKind)
    }

    @Test
    fun `win prefix routes to CREATE_LOG with WIN kind`() {
        val result = NaturalLanguageParser.parse("win: closed the enterprise deal")

        assertEquals(ParsedIntent.CREATE_LOG, result.intent)
        assertEquals(LogKind.WIN, result.logKind)
    }

    // ── Meeting capture ───────────────────────────────────────────────────────

    @Test
    fun `meeting with keyword routes to CREATE_MEETING`() {
        val result = NaturalLanguageParser.parse("meeting with Alice tomorrow at 10am")

        assertEquals(ParsedIntent.CREATE_MEETING, result.intent)
        assertTrue(result.meetingAttendees.contains("Alice"))
    }

    @Test
    fun `call with keyword routes to CREATE_MEETING`() {
        val result = NaturalLanguageParser.parse("call with the design team at 3pm")

        assertEquals(ParsedIntent.CREATE_MEETING, result.intent)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `empty input returns UNKNOWN intent`() {
        val result = NaturalLanguageParser.parse("   ")

        assertEquals(ParsedIntent.UNKNOWN, result.intent)
    }

    @Test
    fun `parse is case-insensitive for keywords`() {
        val result = NaturalLanguageParser.parse("URGENT: Review the PR")

        assertEquals(Priority.URGENT, result.priority)
        assertEquals(ParsedIntent.CREATE_TASK, result.intent)
    }

    @Test
    fun `shorthand priority exclamation mark tags are correctly parsed`() {
        val highResult = NaturalLanguageParser.parse("deploy staging !high")
        assertEquals(Priority.HIGH, highResult.priority)
        assertEquals("deploy staging", highResult.taskTitle)

        val urgentResult = NaturalLanguageParser.parse("database down !urgent")
        assertEquals(Priority.URGENT, urgentResult.priority)
        assertEquals("database down", urgentResult.taskTitle)

        val lowResult = NaturalLanguageParser.parse("archive old logs !low")
        assertEquals(Priority.LOW, lowResult.priority)
        assertEquals("archive old logs", lowResult.taskTitle)
    }

    @Test
    fun `shorthand priority does not trigger on words starting with high or low`() {
        val highlighterResult = NaturalLanguageParser.parse("buy a !highlighter")
        assertEquals(Priority.MEDIUM, highlighterResult.priority)
        assertEquals("buy a !highlighter", highlighterResult.taskTitle)

        val lowriderResult = NaturalLanguageParser.parse("service !lowrider car")
        assertEquals(Priority.MEDIUM, lowriderResult.priority)
        assertEquals("service !lowrider car", lowriderResult.taskTitle)
    }

    @Test
    fun `numeric hashtags like issue numbers are preserved in title and not treated as project`() {
        val result = NaturalLanguageParser.parse("fix bug #123")
        assertEquals(ParsedIntent.CREATE_TASK, result.intent)
        assertEquals("fix bug #123", result.taskTitle)
        assertNull(result.project)
    }

    @Test
    fun `project tag with hash is extracted and stripped from title`() {
        val result = NaturalLanguageParser.parse("q3 budget !high #launch")

        assertEquals(ParsedIntent.CREATE_TASK, result.intent)
        assertEquals("q3 budget", result.taskTitle)
        assertEquals(Priority.HIGH, result.priority)
        assertEquals("launch", result.project)
    }

    @Test
    fun `unicode project tag is extracted`() {
        val result = NaturalLanguageParser.parse("sign contract #projet")
        assertEquals("sign contract", result.taskTitle)
        assertEquals("projet", result.project)
    }

    // ── Multi-clause & Conversational tests ───────────────────────────────────

    @Test
    fun `conversational greetings return CONVERSATION intent`() {
        val hi = NaturalLanguageParser.parse("Hi how are you?")
        assertEquals(ParsedIntent.CONVERSATION, hi.intent)
        assertTrue(hi.conversationReply?.isNotBlank() == true)

        val hello = NaturalLanguageParser.parse("hello!")
        assertEquals(ParsedIntent.CONVERSATION, hello.intent)

        val thanks = NaturalLanguageParser.parse("thank you")
        assertEquals(ParsedIntent.CONVERSATION, thanks.intent)
    }

    @Test
    fun `weekly engineering sync routes to CREATE_MEETING`() {
        val result = NaturalLanguageParser.parse("Weekly Engineering Sync on Friday")
        assertEquals(ParsedIntent.CREATE_MEETING, result.intent)
    }

    @Test
    fun `day after tomorrow keyword sets due date 2 days out`() {
        val today = java.time.LocalDate.of(2026, 9, 11)
        val result = NaturalLanguageParser.parse("buy groceries day after tomorrow", referenceDate = today)

        assertEquals(today.plusDays(2), result.dueDate)
    }

    @Test
    fun `meeting title is cleaned and time is extracted`() {
        val today = java.time.LocalDate.of(2026, 9, 11)
        val result = NaturalLanguageParser.parse("Meeting with Product Design at 3pm", referenceDate = today)
        assertEquals(ParsedIntent.CREATE_MEETING, result.intent)
        assertEquals("Product Design", result.meetingTitle)
        assertEquals(java.time.LocalTime.of(15, 0), result.meetingTime)
        assertTrue(result.meetingAttendees.contains("Product Design"))
    }

    @Test
    fun `relative times like at night extract proper local time`() {
        val time = NaturalLanguageParser.extractTime("call sister at night")
        assertEquals(java.time.LocalTime.of(20, 0), time)
    }

    @Test
    fun `splitClauses splits compound multi-action utterances`() {
        val input = "save two meetings tomorrow with Acme Corp and three tasks for day after tomorrow"
        val clauses = NaturalLanguageParser.splitClauses(input)
        assertTrue(clauses.size >= 2)

        val listInput = "1. Call Alice tomorrow\n2. Review PR today\n3. Weekly sync on Friday"
        val listClauses = NaturalLanguageParser.splitClauses(listInput)
        assertEquals(3, listClauses.size)
    }

    @Test
    fun `parseMulti produces multiple structured items from compound input`() {
        val input = "call Alice tomorrow and finish the slides today and meeting with Bob on Friday"
        val results = NaturalLanguageParser.parseMulti(input)
        assertTrue(results.size >= 2)
        assertTrue(results.any { it.intent == ParsedIntent.CREATE_TASK })
        assertTrue(results.any { it.intent == ParsedIntent.CREATE_MEETING })
    }
}
