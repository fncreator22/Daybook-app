package com.sr2ma.daybook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistParserTest {

    @Test
    fun parse_emptyNotes_returnsEmptyList() {
        val items = ChecklistParser.parse("")
        assertTrue(items.isEmpty())
    }

    @Test
    fun parse_checklistItems_identifiesCheckedAndUnchecked() {
        val notes = """
            Here is a task overview
            - [ ] 1. Review Q3 Analytics
            - [x] 2. Competitor Research
            - [X] 3. Outline Objectives
            * [ ] 4. Draft Campaign Concepts
            + [ ] 5. Extra task item
            Location: Meeting Room 4
        """.trimIndent()

        val items = ChecklistParser.parse(notes)
        assertEquals(5, items.size)

        assertEquals("1. Review Q3 Analytics", items[0].text)
        assertFalse(items[0].isChecked)

        assertEquals("2. Competitor Research", items[1].text)
        assertTrue(items[1].isChecked)

        assertEquals("3. Outline Objectives", items[2].text)
        assertTrue(items[2].isChecked)

        assertEquals("4. Draft Campaign Concepts", items[3].text)
        assertFalse(items[3].isChecked)

        assertEquals("5. Extra task item", items[4].text)
        assertFalse(items[4].isChecked)
    }

    @Test
    fun getSummary_calculatesCorrectFraction() {
        val items = listOf(
            ChecklistParser.ChecklistItem(0, "A", false, "- [ ] A"),
            ChecklistParser.ChecklistItem(1, "B", true, "- [x] B"),
            ChecklistParser.ChecklistItem(2, "C", true, "- [x] C"),
            ChecklistParser.ChecklistItem(3, "D", false, "- [ ] D"),
            ChecklistParser.ChecklistItem(4, "E", false, "- [ ] E"),
        )
        val summary = ChecklistParser.getSummary(items)
        assertEquals(5, summary.total)
        assertEquals(2, summary.completed)
        assertEquals(0.4f, summary.progressFraction, 0.001f)
        assertEquals("2/5", summary.progressText)
    }

    @Test
    fun getSummary_emptyList_returnsZeroProgress() {
        val summary = ChecklistParser.getSummary(emptyList())
        assertEquals(0, summary.total)
        assertEquals(0, summary.completed)
        assertEquals(0f, summary.progressFraction, 0.001f)
        assertEquals("0/0", summary.progressText)
    }

    @Test
    fun toggleItem_togglesUncheckedToChecked() {
        val initial = "- [ ] Item 1\n- [ ] Item 2"
        val toggled = ChecklistParser.toggleItem(initial, 0)
        assertEquals("- [x] Item 1\n- [ ] Item 2", toggled)

        val toggledBack = ChecklistParser.toggleItem(toggled, 0)
        assertEquals("- [ ] Item 1\n- [ ] Item 2", toggledBack)
    }

    @Test
    fun toggleItem_invalidIndex_returnsUntouched() {
        val initial = "- [ ] Item 1\n- [ ] Item 2"
        val toggled = ChecklistParser.toggleItem(initial, 5)
        assertEquals(initial, toggled)

        val negativeIndex = ChecklistParser.toggleItem(initial, -1)
        assertEquals(initial, negativeIndex)
    }

    @Test
    fun toggleItem_preservesSurroundingTextAndIndentation() {
        val notes = """
            Header note
              - [ ] Indented subtask
            Footer note
        """.trimIndent()

        val toggled = ChecklistParser.toggleItem(notes, 0)
        val expected = """
            Header note
              - [x] Indented subtask
            Footer note
        """.trimIndent()
        assertEquals(expected, toggled)
    }

    @Test
    fun appendComment_appendsToExisting() {
        val base = "Initial note."
        val updated = ChecklistParser.appendComment(base, "Followed up with team.")
        assertEquals("Initial note.\nFollowed up with team.", updated)
    }

    @Test
    fun appendComment_emptyBase_returnsComment() {
        val updated = ChecklistParser.appendComment("", "First note.")
        assertEquals("First note.", updated)
    }

    @Test
    fun appendSubtask_addsNewChecklistLine() {
        val base = "- [x] Done item"
        val updated = ChecklistParser.appendSubtask(base, "New follow up task")
        assertEquals("- [x] Done item\n- [ ] New follow up task", updated)
    }

    @Test
    fun appendSubtask_emptyBase_returnsSingleChecklist() {
        val updated = ChecklistParser.appendSubtask("", "First subtask")
        assertEquals("- [ ] First subtask", updated)
    }

    @Test
    fun extractDescription_filtersOutChecklistAndMetadata() {
        val notes = """
            Analyze Q3 data, brainstorm campaigns for new product launch.
            Time: 10:00 AM
            Location: Conf Room A
            Attachments: Deck.pdf, Research.xlsx
            - [ ] Review slides
            - [x] Send invite
        """.trimIndent()

        val description = ChecklistParser.extractDescription(notes)
        assertEquals("Analyze Q3 data, brainstorm campaigns for new product launch.", description)
    }

    @Test
    fun extractAttachments_extractsFromLinesAndProse() {
        val notes = """
            Please review the attached presentation.
            Attachments: Deck.pdf, Research.xlsx
            Also check backup_plan.docx
        """.trimIndent()

        val attachments = ChecklistParser.extractAttachments(notes)
        assertEquals(listOf("Deck.pdf", "Research.xlsx", "backup_plan.docx"), attachments)
    }

    @Test
    fun extractAttachments_emptyNotes_returnsEmptyList() {
        val attachments = ChecklistParser.extractAttachments("")
        assertTrue(attachments.isEmpty())
    }

    @Test
    fun extractTopics_extractsHashtagsAndProject_caseInsensitiveDeduplication() {
        val notes = "Discuss Q4 plan #Marketing #Strategy and #goals and #marketing"
        val topics = ChecklistParser.extractTopics(notes, project = "Quarterly")
        assertEquals(listOf("#Quarterly", "#Marketing", "#Strategy", "#goals"), topics)
    }
}
