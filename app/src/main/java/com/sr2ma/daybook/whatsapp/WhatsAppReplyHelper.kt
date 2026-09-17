package com.sr2ma.daybook.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Composes a WhatsApp reply via Android's share/deep-link intent.
 *
 * This NEVER auto-sends a message. It opens WhatsApp with the chat pre-filled;
 * the user must tap Send manually. This keeps the flow within WhatsApp's ToS
 * and gives the user full control.
 *
 * Deep-link format: whatsapp://send?phone=<number>&text=<message>
 * For contacts without a phone number (group chats, display-name only), we fall
 * back to the ACTION_SEND intent with the WhatsApp package targeted.
 */
object WhatsAppReplyHelper {

    /**
     * Opens WhatsApp with [text] pre-filled for the given [sender].
     * [phoneNumber] is optional — if null, a generic share intent opens the
     * contact picker inside WhatsApp.
     */
    fun openReply(context: Context, sender: String, text: String, phoneNumber: String? = null) {
        val intent = if (phoneNumber != null) {
            // Direct chat deep-link — digits only, no + or spaces.
            val digits = phoneNumber.filter { it.isDigit() }
            Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$digits&text=${Uri.encode(text)}"))
        } else {
            // Generic share — WhatsApp shows the contact picker.
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.whatsapp")
            }
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // WhatsApp not installed — open Play Store.
            val fallback = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.whatsapp"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }
}
