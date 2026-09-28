package com.sr2ma.daybook.sync

import com.sr2ma.daybook.domain.model.GmailMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class GmailSyncEngineTest {

    private lateinit var syncPrefs: SyncPreferences
    private lateinit var engine: GmailSyncEngine

    @Before
    fun setUp() {
        val fakePrefs = FakeSharedPreferences()
        syncPrefs = SyncPreferences(customPrefs = fakePrefs)
        // Pass null or dummy for GmailDao since we're testing pure logic functions
        // For sync test, we can verify category classification and action extraction
    }

    @Test
    fun `classifyCategory correctly identifies promotions, spam, updates, and primary emails`() {
        // We can create a dummy engine using a mock or null if constructor allows,
        // or check classifyCategory directly if we instantiate engine or test its logic.
        val dummyEngine = GmailSyncEngine(
            syncPrefs = syncPrefs,
        )

        // Promotions
        assertEquals(
            GmailMessage.CATEGORY_PROMOTIONS,
            dummyEngine.classifyCategory("newsletter@store.com", "50% off summer sale!", "Unsubscribe from this newsletter"),
        )

        // Spam
        assertEquals(
            GmailMessage.CATEGORY_SPAM,
            dummyEngine.classifyCategory("stranger@unknown.com", "You won the lottery! Claim prize", "Wire funds to claim"),
        )

        // Updates
        assertEquals(
            GmailMessage.CATEGORY_UPDATES,
            dummyEngine.classifyCategory("noreply@service.com", "Security alert: new login", "Your verification code is 123456"),
        )

        // Primary
        assertEquals(
            GmailMessage.CATEGORY_PRIMARY,
            dummyEngine.classifyCategory("boss@work.com", "Project deadline update", "Please find the status attached"),
        )
    }

    @Test
    fun `extractActionSuggestion extracts task and meeting intents from email content`() {
        val dummyEngine = GmailSyncEngine(
            syncPrefs = syncPrefs,
        )
        val today = LocalDate.of(2026, 9, 25)

        val taskSuggestion = dummyEngine.extractActionSuggestion(
            subject = "Task: Finish Q3 budget report by tomorrow",
            snippet = "Please make sure the financial forecast is ready.",
            referenceDate = today,
        )
        assertNotNull(taskSuggestion)
        assertTrue(taskSuggestion!!.startsWith("Task:"))

        val meetingSuggestion = dummyEngine.extractActionSuggestion(
            subject = "Meeting: Product Design Review at 3pm",
            snippet = "Let's review the onboarding flow mockups.",
            referenceDate = today,
        )
        assertNotNull(meetingSuggestion)
        assertTrue(meetingSuggestion!!.startsWith("Meeting:"))

        val noAction = dummyEngine.extractActionSuggestion(
            subject = "Casual update",
            snippet = "Just wanted to say hello, nothing needed.",
            referenceDate = today,
        )
        assertNull(noAction)
    }

    @Test
    fun `extractTopics extracts topics and subtopics from tags and subjects`() {
        val topics = GmailSyncEngine.extractTopics(
            subject = "[Project/Mobile] Fix login crash #bug/auth",
            snippet = "Review the latest stacktrace for the auth flow.",
        )
        assertTrue(topics.any { it.contains("Project/Mobile", ignoreCase = true) })
        assertTrue(topics.any { it.contains("bug/auth", ignoreCase = true) })
    }

    @Test
    fun `extractLocation detects video and physical meeting locations`() {
        val locZoom = GmailSyncEngine.extractLocation("Sync call", "Join Zoom meeting at zoom.us/j/123456")
        assertEquals("Zoom", locZoom)

        val locMeet = GmailSyncEngine.extractLocation("Sprint Planning", "Video call link: https://meet.google.com/abc-defg-hij")
        assertEquals("Google Meet", locMeet)

        val locRoom = GmailSyncEngine.extractLocation("Design Session", "Let's meet in Room 302 on the third floor.")
        assertEquals("Room 302", locRoom)
    }

    @Test
    fun `extractDateTime correctly extracts due date and meeting time`() {
        val today = LocalDate.of(2026, 9, 28)
        val (date, time) = GmailSyncEngine.extractDateTime(
            subject = "Meeting tomorrow at 3pm",
            snippet = "Discuss quarterly roadmap.",
            referenceDate = today,
        )
        assertEquals(today.plusDays(1), date)
        assertEquals(java.time.LocalTime.of(15, 0), time)
    }

    @Test
    fun `buildTaskNotes preserves rich metadata and message ID`() {
        val msg = GmailMessage(
            id = 42L,
            messageId = "msg_abc123",
            sender = "alice@example.com",
            subject = "Quarterly Sync",
            snippet = "Please prepare the slide deck.",
            receivedAt = System.currentTimeMillis(),
        )
        val notes = GmailSyncEngine.buildTaskNotes(
            msg = msg,
            topics = listOf("Finance/Budget"),
            location = "Zoom",
            dateTimeStr = "15:00",
        )
        assertTrue(notes.contains("Source: Email from alice@example.com [msgId:msg_abc123]"))
        assertTrue(notes.contains("Subject: Quarterly Sync"))
        assertTrue(notes.contains("Topics: Finance/Budget"))
        assertTrue(notes.contains("Location: Zoom"))
        assertTrue(notes.contains("Time: 15:00"))
        assertTrue(notes.contains("Please prepare the slide deck."))
    }

    @Test
    fun `offline sync returns 0 when demoDataEnabled is false`() = runBlocking {
        syncPrefs.accessToken = "offline_local_test"
        syncPrefs.profilePrimaryEmail = "test@example.com"
        syncPrefs.demoDataEnabled = false

        val dummyEngine = GmailSyncEngine(syncPrefs = syncPrefs)
        val result = dummyEngine.sync()

        assertTrue(result is GmailSyncEngine.SyncResult.Success)
        val success = result as GmailSyncEngine.SyncResult.Success
        assertEquals(0, success.fetchedCount)
        assertEquals(0, success.actionableCount)
    }

    @Test
    fun `offline sync generates samples only when demoDataEnabled is true`() = runBlocking {
        syncPrefs.accessToken = "offline_local_test"
        syncPrefs.profilePrimaryEmail = "test@example.com"
        syncPrefs.demoDataEnabled = true

        val dummyEngine = GmailSyncEngine(syncPrefs = syncPrefs)
        val result = dummyEngine.sync()

        assertTrue(result is GmailSyncEngine.SyncResult.Success)
        val success = result as GmailSyncEngine.SyncResult.Success
        assertEquals(3, success.fetchedCount)
        assertTrue(success.actionableCount > 0)
    }
}
