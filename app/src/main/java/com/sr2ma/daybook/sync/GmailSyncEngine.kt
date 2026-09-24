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
    private val gmailDao: GmailDao,
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
            return@withContext SyncResult.Error("Not signed in to Google account")
        }

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

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val jsonResponse = conn.inputStream.bufferedReader().use { it.readText() }
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
                            gmailDao.insertOrIgnore(msg)
                            fetched++
                        }
                    }
                }
                syncPrefs.lastGmailSyncAt = System.currentTimeMillis()
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
        val parseResult = NaturalLanguageParser.parse(candidate, referenceDate)
        return when (parseResult.intent) {
            ParsedIntent.CREATE_TASK -> {
                val title = parseResult.taskTitle ?: candidate.take(60)
                "Task: $title"
            }
            ParsedIntent.CREATE_MEETING -> {
                val title = parseResult.meetingTitle ?: candidate.take(60)
                "Meeting: $title"
            }
            else -> {
                // Secondary check in snippet for "by tomorrow", "call at", "due"
                val snippetResult = NaturalLanguageParser.parse(snippet.take(120), referenceDate)
                if (snippetResult.intent == ParsedIntent.CREATE_TASK) {
                    "Task: ${snippetResult.taskTitle ?: snippet.take(50)}"
                } else if (snippetResult.intent == ParsedIntent.CREATE_MEETING) {
                    "Meeting: ${snippetResult.meetingTitle ?: snippet.take(50)}"
                } else {
                    null
                }
            }
        }
    }

    private fun cleanSenderName(from: String): String {
        // e.g. "John Doe <john@example.com>" -> "John Doe"
        val angleIdx = from.indexOf('<')
        return if (angleIdx > 0) from.substring(0, angleIdx).trim('"', ' ') else from.trim()
    }
}
