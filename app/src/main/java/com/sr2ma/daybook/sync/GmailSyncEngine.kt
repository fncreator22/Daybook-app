package com.sr2ma.daybook.sync

import com.sr2ma.daybook.data.dao.GmailDao
import com.sr2ma.daybook.domain.NaturalLanguageParser
import com.sr2ma.daybook.domain.ParsedIntent
import com.sr2ma.daybook.domain.model.GmailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * On-device Gmail message synchronization and actionable task/meeting extraction.
 *
 * Rules:
 * - Only operates when user is signed in with Google Auth and opted into Gmail sync.
 * - Filters spam and promotional/marketing newsletters locally.
 * - Extracts action items using [NaturalLanguageParser] so tasks and meetings
 *   can be created with a single user tap.
 */
class GmailSyncEngine(
    private val gmailDao: GmailDao? = null,
    private val syncPrefs: SyncPreferences,
) {

    sealed interface SyncResult {
        data class Success(val fetchedCount: Int, val actionableCount: Int) : SyncResult
        data class Error(val message: String) : SyncResult
    }

    /**
     * Synchronizes recent messages from Gmail if an access token is available.
     * If no active network or token is expired, extracts actions from existing cache.
     */
    suspend fun sync(referenceDate: LocalDate = LocalDate.now()): SyncResult = withContext(Dispatchers.IO) {
        val token = syncPrefs.accessToken
        if (token.isNullOrBlank()) {
            return@withContext SyncResult.Error("Not signed in to Google account or offline profile")
        }

        // ── Offline Profile Mode: Extract actions across verified emails ──────────
        if (token.startsWith("offline_")) {
            if (!syncPrefs.demoDataEnabled || syncPrefs.sampleGmailCleared) {
                syncPrefs.lastGmailSyncAt = System.currentTimeMillis()
                return@withContext SyncResult.Success(fetchedCount = 0, actionableCount = 0)
            }
            val verified = syncPrefs.verifiedEmails.ifEmpty {
                setOfNotNull(syncPrefs.profilePrimaryEmail ?: syncPrefs.accountEmail)
            }
            if (verified.isEmpty()) {
                return@withContext SyncResult.Error("No verified email accounts. Please configure your email in Profile.")
            }

            var fetched = 0
            var actionable = 0
            val now = System.currentTimeMillis()

            verified.forEachIndexed { accIdx, email ->
                val sampleEmails = listOf(
                    Triple(
                        "[Sample] Task: Complete Q3 Roadmap Review by tomorrow",
                        "Hi team, please review and finalize the Q3 roadmap document by tomorrow afternoon.",
                        "Alex Morgan <alex@company.com>",
                    ),
                    Triple(
                        "[Sample] Meeting with Product Design at 3pm",
                        "Let's sync to review the mobile navigation mockups at 3pm today.",
                        "Design Team <design@company.com>",
                    ),
                    Triple(
                        "[Sample] Weekly Engineering Sync on Friday",
                        "Reminder for our weekly engineering sync on Friday at 11am.",
                        "Eng Lead <lead@company.com>",
                    ),
                )

                sampleEmails.forEachIndexed { msgIdx, (subj, snip, sndr) ->
                    val msgId = "offline_${email.hashCode()}_${accIdx}_$msgIdx"
                    val category = classifyCategory(sndr, subj, snip)
                    val suggestion = extractActionSuggestion(subj, snip, referenceDate)
                    if (suggestion != null) actionable++

                    val msg = GmailMessage(
                        messageId = msgId,
                        sender = cleanSenderName(sndr),
                        subject = subj,
                        snippet = "[$email] $snip",
                        receivedAt = now - (msgIdx * 3_600_000L),
                        isRead = false,
                        category = category,
                        suggestedAction = suggestion,
                    )
                    gmailDao?.insertOrIgnore(msg)
                    fetched++
                }
            }

            syncPrefs.lastGmailSyncAt = System.currentTimeMillis()
            return@withContext SyncResult.Success(fetchedCount = fetched, actionableCount = actionable)
        }

        // ── Online Mode: Record network access and call Gmail API ────────────────
        syncPrefs.recordNetworkAccess()

        try {
            // Fetch list of recent message IDs from Gmail REST API
            val listUrl = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=15")
            val conn = (listUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val responseCode: Int
            val jsonResponse: String?
            try {
                responseCode = conn.responseCode
                jsonResponse = if (responseCode == 200) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else null
            } finally {
                conn.disconnect()
            }

            if (responseCode == 200 && jsonResponse != null) {
                val root = JSONObject(jsonResponse)
                val messagesArray = root.optJSONArray("messages")

                var fetched = 0
                var actionable = 0

                if (messagesArray != null) {
                    for (i in 0 until messagesArray.length()) {
                        val msgObj = messagesArray.getJSONObject(i)
                        val msgId = msgObj.optString("id") ?: continue

                        // Fetch individual message details
                        val detailUrl = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/$msgId?format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date")
                        val detailConn = (detailUrl.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            setRequestProperty("Authorization", "Bearer $token")
                            setRequestProperty("Accept", "application/json")
                            connectTimeout = 8_000
                            readTimeout = 8_000
                        }

                        try {
                            if (detailConn.responseCode == 200) {
                                val detailJson = detailConn.inputStream.bufferedReader().use { it.readText() }
                                val detail = JSONObject(detailJson)
                                val snippet = detail.optString("snippet", "")
                                val internalDate = detail.optLong("internalDate", System.currentTimeMillis())

                                var from = "Unknown"
                                var subject = "No subject"

                                val headers = detail.optJSONObject("payload")?.optJSONArray("headers")
                                if (headers != null) {
                                    for (h in 0 until headers.length()) {
                                        val header = headers.getJSONObject(h)
                                        val name = header.optString("name")
                                        val value = header.optString("value")
                                        if (name.equals("From", ignoreCase = true)) from = value
                                        if (name.equals("Subject", ignoreCase = true)) subject = value
                                    }
                                }

                                // Classify category (primary, updates, promotions, spam)
                                val category = classifyCategory(from, subject, snippet)

                                // Extract action suggestion via NLP
                                val suggestion = extractActionSuggestion(subject, snippet, referenceDate)
                                if (suggestion != null) actionable++

                                val msg = GmailMessage(
                                    messageId = msgId,
                                    sender = cleanSenderName(from),
                                    subject = subject,
                                    snippet = snippet,
                                    receivedAt = internalDate,
                                    isRead = false,
                                    category = category,
                                    suggestedAction = suggestion,
                                )
                                gmailDao?.insertOrIgnore(msg)
                                fetched++
                            }
                        } finally {
                            detailConn.disconnect()
                        }
                    }
                }
                syncPrefs.lastGmailSyncAt = System.currentTimeMillis()
                com.sr2ma.daybook.logging.DaybookLogger.i(null, "GmailSync", "Sync completed: $fetched fetched, $actionable actionable")
                SyncResult.Success(fetchedCount = fetched, actionableCount = actionable)
            } else if (responseCode == 401) {
                SyncResult.Error("Google Auth session expired. Please sign in again.")
            } else {
                SyncResult.Error("Gmail sync failed with HTTP $responseCode")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Network error during Gmail sync")
        }
    }

    /**
     * Pure on-device heuristic classifier to filter spam and marketing emails.
     */
    fun classifyCategory(from: String, subject: String, snippet: String): String {
        val combined = "$from $subject $snippet".lowercase()
        return when {
            combined.contains("unsubscribe") || combined.contains("view in browser") ||
            combined.contains("newsletter") || combined.contains("marketing") ||
            combined.contains("promo") || combined.contains("discount") || combined.contains("% off") ->
                GmailMessage.CATEGORY_PROMOTIONS

            combined.contains("lottery") || combined.contains("claim prize") ||
            combined.contains("wire funds") || combined.contains("urgent inheritance") ->
                GmailMessage.CATEGORY_SPAM

            combined.contains("no-reply") || combined.contains("noreply") ||
            combined.contains("security alert") || combined.contains("verification code") ->
                GmailMessage.CATEGORY_UPDATES

            else -> GmailMessage.CATEGORY_PRIMARY
        }
    }

    /**
     * Extracts an actionable suggestion (e.g. "Task: Submit budget" or "Meeting: Client catch-up")
     * using [NaturalLanguageParser].
     */
    fun extractActionSuggestion(subject: String, snippet: String, referenceDate: LocalDate): String? {
        val candidate = if (subject.isNotBlank() && subject != "No subject") subject else snippet.take(80)
        val combined = "$subject $snippet".lowercase()
        val parseResult = NaturalLanguageParser.parse(candidate, referenceDate)

        if (parseResult.intent == ParsedIntent.CREATE_MEETING) {
            val raw = parseResult.meetingTitle ?: candidate.take(60)
            val clean = cleanMeetingTitleSuggestion(raw)
            return "Meeting: $clean"
        }

        // For tasks, require an action marker (due date, task keyword, urgent priority, or explicit request)
        val hasActionMarker = parseResult.dueDate != null ||
            parseResult.priority != com.sr2ma.daybook.domain.model.Priority.MEDIUM ||
            listOf("task", "todo", "action", "deadline", "due", "please", "review", "submit", "finish", "urgent", "asap")
                .any { combined.contains(it) }

        if (hasActionMarker && parseResult.intent == ParsedIntent.CREATE_TASK) {
            val raw = parseResult.taskTitle ?: candidate.take(60)
            val clean = raw.replace(Regex("^(task:?\\s*|\\[sample\\]\\s*)+", RegexOption.IGNORE_CASE), "").trim()
            return "Task: $clean"
        }

        // Secondary check in snippet
        val snippetResult = NaturalLanguageParser.parse(snippet.take(120), referenceDate)
        if (snippetResult.intent == ParsedIntent.CREATE_MEETING) {
            val raw = snippetResult.meetingTitle ?: snippet.take(50)
            val clean = cleanMeetingTitleSuggestion(raw)
            return "Meeting: $clean"
        }
        if (snippetResult.dueDate != null && snippetResult.intent == ParsedIntent.CREATE_TASK) {
            val raw = snippetResult.taskTitle ?: snippet.take(50)
            val clean = raw.replace(Regex("^(task:?\\s*|\\[sample\\]\\s*)+", RegexOption.IGNORE_CASE), "").trim()
            return "Task: $clean"
        }

        return null
    }

    private fun cleanMeetingTitleSuggestion(raw: String): String {
        return raw.replace(Regex("^(meeting:?\\s*|\\[sample\\]\\s*|call:?\\s*|sync:?\\s*)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^with\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+at\\s+\\d+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+on\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|today|tomorrow).*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { raw.trim() }
    }

    private fun cleanSenderName(from: String): String {
        // e.g. "John Doe <john@example.com>" -> "John Doe"
        val angleIdx = from.indexOf('<')
        return if (angleIdx > 0) from.substring(0, angleIdx).trim('"', ' ') else from.trim()
    }

    companion object {
        fun extractTopics(subject: String, snippet: String): List<String> {
            val topics = mutableListOf<String>()
            val bracketRegex = Regex("\\[([A-Za-z0-9_ /-]+)\\]")
            bracketRegex.findAll(subject).forEach {
                val tag = it.groupValues[1].trim()
                if (tag.isNotEmpty() && !tag.equals("Sample", ignoreCase = true)) {
                    topics.add(tag)
                    if (tag.contains("/")) {
                        tag.split("/").map { p -> p.trim() }.filter { p -> p.isNotEmpty() }.forEach { p -> topics.add(p) }
                    }
                }
            }
            val hashRegex = Regex("#([A-Za-z0-9_/-]+)")
            hashRegex.findAll("$subject $snippet").forEach {
                val tag = it.groupValues[1].trim()
                if (tag.isNotEmpty()) {
                    topics.add(tag)
                    if (tag.contains("/")) {
                        tag.split("/").map { p -> p.trim() }.filter { p -> p.isNotEmpty() }.forEach { p -> topics.add(p) }
                    }
                }
            }
            val keywords = listOf("Roadmap", "Design", "Engineering", "Marketing", "Finance", "Budget", "Security", "Launch", "Release", "Sprint", "Backend", "Frontend", "QA")
            for (k in keywords) {
                if ("$subject $snippet".contains(k, ignoreCase = true) && !topics.contains(k)) {
                    topics.add(k)
                }
            }
            return topics.distinct()
        }

        fun extractLocation(subject: String, snippet: String): String? {
            val combined = "$subject $snippet"
            val locationRegex = Regex("(?i)\\b(?:in|at|on)\\s+(Zoom|Google Meet|Meet|Teams|Room\\s+[A-Za-z0-9]+|Office|Cafeteria|Starbucks)\\b")
            val match = locationRegex.find(combined)
            if (match != null) {
                val rawLoc = match.groupValues[1].trim()
                if (rawLoc.equals("zoom", ignoreCase = true)) return "Zoom"
                if (rawLoc.equals("google meet", ignoreCase = true) || rawLoc.equals("meet", ignoreCase = true)) return "Google Meet"
                if (rawLoc.equals("teams", ignoreCase = true)) return "Microsoft Teams"
                if (rawLoc.startsWith("room", ignoreCase = true)) {
                    val parts = rawLoc.split("\\s+".toRegex(), 2)
                    return "Room " + (parts.getOrNull(1)?.uppercase() ?: "")
                }
                return rawLoc.replaceFirstChar { it.uppercase() }
            }
            if (combined.contains("zoom", ignoreCase = true)) return "Zoom"
            if (combined.contains("google meet", ignoreCase = true) || combined.contains("meet.google", ignoreCase = true)) return "Google Meet"
            if (combined.contains("teams", ignoreCase = true)) return "Microsoft Teams"
            return null
        }

        fun extractDateTime(subject: String, snippet: String, referenceDate: LocalDate): Pair<LocalDate?, java.time.LocalTime?> {
            val candidate = if (subject.isNotBlank() && subject != "No subject") "$subject $snippet" else snippet
            val parsed = NaturalLanguageParser.parse(candidate, referenceDate)
            return Pair(parsed.dueDate, parsed.meetingTime)
        }

        fun buildTaskNotes(
            msg: GmailMessage,
            topics: List<String>,
            location: String?,
            dateTimeStr: String? = null,
        ): String {
            val sb = StringBuilder()
            sb.append("Source: Email from ${msg.sender} [msgId:${msg.messageId}]\n")
            sb.append("Subject: ${msg.subject}\n")
            if (topics.isNotEmpty()) {
                sb.append("Topics: ${topics.joinToString(", ")}\n")
            }
            if (!location.isNullOrBlank()) {
                sb.append("Location: $location\n")
            }
            if (!dateTimeStr.isNullOrBlank()) {
                sb.append("Time: $dateTimeStr\n")
            }
            sb.append("\n${msg.snippet.trim()}")
            return sb.toString().trim()
        }
    }
}
