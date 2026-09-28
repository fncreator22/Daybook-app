package com.sr2ma.daybook.domain

/**
 * Parses and updates Markdown-style checklist subtasks embedded in task notes.
 *
 * Supported formats:
 * - ` - [ ] Review design specs`
 * - ` - [x] Prepare slide deck`
 * - ` * [ ] Outline objectives`
 * - ` * [X] Completed item`
 * - ` + [ ] Alternate bullet item`
 *
 * 100% offline, deterministic regex parser.
 */
object ChecklistParser {

    private val CHECKLIST_LINE_REGEX = Regex("""^(\s*[-*+]\s*\[)([ xX])(\]\s*)(.*)$""")
    private val HASHTAG_REGEX = Regex("""#([A-Za-z0-9_-]+)""")
    private val ATTACHMENT_REGEX = Regex("""\b[\w\-.]+\.(?:pdf|xlsx?|docx?|pptx?|png|jpe?g|csv|txt)\b""", RegexOption.IGNORE_CASE)

    data class ChecklistItem(
        val index: Int,
        val text: String,
        val isChecked: Boolean,
        val rawLine: String,
    )

    data class ChecklistSummary(
        val total: Int,
        val completed: Int,
    ) {
        val progressFraction: Float
            get() = if (total == 0) 0f else completed.toFloat() / total.toFloat()

        val progressText: String
            get() = "$completed/$total"
    }

    /**
     * Parses all checklist items found in [notes].
     */
    fun parse(notes: String): List<ChecklistItem> {
        if (notes.isBlank()) return emptyList()
        val items = mutableListOf<ChecklistItem>()
        var currentIndex = 0
        notes.lines().forEach { line ->
            val match = CHECKLIST_LINE_REGEX.matchEntire(line)
            if (match != null) {
                val mark = match.groupValues[2]
                val itemText = match.groupValues[4].trim()
                items.add(
                    ChecklistItem(
                        index = currentIndex++,
                        text = itemText,
                        isChecked = mark.equals("x", ignoreCase = true),
                        rawLine = line,
                    )
                )
            }
        }
        return items
    }

    /**
     * Calculates total and completed subtask metrics.
     */
    fun getSummary(items: List<ChecklistItem>): ChecklistSummary {
        return ChecklistSummary(
            total = items.size,
            completed = items.count { it.isChecked },
        )
    }

    /**
     * Toggles the checklist state of the subtask at [itemIndex].
     * Preserves all other lines, metadata, and indentation in [notes].
     */
    fun toggleItem(notes: String, itemIndex: Int): String {
        if (notes.isBlank() || itemIndex < 0) return notes
        val lines = notes.lines().toMutableList()
        var currentMatchIndex = 0
        for (i in lines.indices) {
            val match = CHECKLIST_LINE_REGEX.matchEntire(lines[i])
            if (match != null) {
                if (currentMatchIndex == itemIndex) {
                    val prefix = match.groupValues[1]
                    val mark = match.groupValues[2]
                    val suffix = match.groupValues[3]
                    val text = match.groupValues[4]
                    val newMark = if (mark.equals("x", ignoreCase = true)) " " else "x"
                    lines[i] = "$prefix$newMark$suffix$text"
                    break
                }
                currentMatchIndex++
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Appends a new note or comment to [notes].
     */
    fun appendComment(notes: String, comment: String): String {
        val trimmedComment = comment.trim()
        if (trimmedComment.isEmpty()) return notes
        return if (notes.isBlank()) {
            trimmedComment
        } else {
            "${notes.trimEnd()}\n$trimmedComment"
        }
    }

    /**
     * Appends a new checklist subtask (`- [ ] text`) to [notes].
     */
    fun appendSubtask(notes: String, subtaskText: String): String {
        val trimmed = subtaskText.trim()
        if (trimmed.isEmpty()) return notes
        val checklistLine = "- [ ] $trimmed"
        return if (notes.isBlank()) {
            checklistLine
        } else {
            "${notes.trimEnd()}\n$checklistLine"
        }
    }

    /**
     * Extracts non-checklist, non-metadata narrative description from [notes].
     */
    fun extractDescription(notes: String): String {
        if (notes.isBlank()) return ""
        val contentLines = notes.lines().filter { line ->
            val trimmed = line.trim()
            !CHECKLIST_LINE_REGEX.matches(line) &&
                !trimmed.startsWith("Time:", ignoreCase = true) &&
                !trimmed.startsWith("Location:", ignoreCase = true) &&
                !trimmed.startsWith("Source:", ignoreCase = true) &&
                !trimmed.startsWith("Attachments:", ignoreCase = true) &&
                !trimmed.startsWith("Attachment:", ignoreCase = true) &&
                !trimmed.startsWith("[msgId:", ignoreCase = true)
        }
        return contentLines.joinToString("\n").trim()
    }

    /**
     * Extracts document/attachment references from [notes].
     */
    fun extractAttachments(notes: String): List<String> {
        if (notes.isBlank()) return emptyList()
        val attachments = mutableListOf<String>()
        notes.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Attachments:", ignoreCase = true) || trimmed.startsWith("Attachment:", ignoreCase = true)) {
                val files = trimmed.substringAfter(":").split(',', ';')
                files.forEach { f ->
                    val fileTrimmed = f.trim()
                    if (fileTrimmed.isNotEmpty() && !attachments.any { it.equals(fileTrimmed, ignoreCase = true) }) {
                        attachments.add(fileTrimmed)
                    }
                }
            }
        }
        ATTACHMENT_REGEX.findAll(notes).forEach { match ->
            val fileName = match.value
            if (!attachments.any { it.equals(fileName, ignoreCase = true) }) {
                attachments.add(fileName)
            }
        }
        return attachments
    }

    /**
     * Extracts hashtags and topics from [notes] and [project].
     */
    fun extractTopics(notes: String, project: String? = null): List<String> {
        val topics = mutableListOf<String>()
        if (!project.isNullOrBlank()) {
            val projTag = if (project.startsWith("#")) project else "#$project"
            topics.add(projTag)
        }
        HASHTAG_REGEX.findAll(notes).forEach { match ->
            val tag = "#${match.groupValues[1]}"
            if (!topics.any { it.equals(tag, ignoreCase = true) }) {
                topics.add(tag)
            }
        }
        return topics
    }
}
