package com.sr2ma.daybook.domain.model

/**
 * An email message retrieved from Gmail or ingested for action extraction.
 *
 * All processing is on-device. Spam and marketing messages are tagged
 * and filtered out according to user settings. Action items found by
 * [com.sr2ma.daybook.domain.NaturalLanguageParser] are attached as [suggestedAction].
 */
data class GmailMessage(
    val id: Long = 0,
    val messageId: String,
    val sender: String,
    val subject: String,
    val snippet: String,
    val receivedAt: Long,
    val isRead: Boolean = false,
    val category: String = CATEGORY_PRIMARY,
    val suggestedAction: String? = null,
    val actionedAt: Long? = null,
) {
    val isSpamOrMarketing: Boolean
        get() = category.equals(CATEGORY_SPAM, ignoreCase = true) ||
                category.equals(CATEGORY_PROMOTIONS, ignoreCase = true)

    companion object {
        const val CATEGORY_PRIMARY = "primary"
        const val CATEGORY_UPDATES = "updates"
        const val CATEGORY_PROMOTIONS = "promotions"
        const val CATEGORY_SPAM = "spam"
    }
}
