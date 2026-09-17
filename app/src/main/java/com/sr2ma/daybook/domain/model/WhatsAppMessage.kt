package com.sr2ma.daybook.domain.model

/**
 * A single WhatsApp notification captured by [com.sr2ma.daybook.whatsapp.WhatsAppListenerService].
 *
 * Only the sender display-name and the notification text (the preview Android
 * shows in the shade) are stored — the full chat history is never accessible.
 * The user can reply via a pre-filled Intent; the reply text is stored here for
 * the conversation log shown in the Today tab.
 */
data class WhatsAppMessage(
    val id: Long = 0,
    val sender: String,
    val message: String,
    val receivedAt: Long,
    val repliedText: String? = null,
    val repliedAt: Long? = null,
    val notificationKey: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
