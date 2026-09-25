package com.sr2ma.daybook.sync

import com.sr2ma.daybook.domain.model.GmailMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
}
