package com.sr2ma.daybook.ui.theme

import androidx.compose.ui.graphics.Color

// The palette is built around the teal in the launcher icon on a warm paper
// background. Only a light scheme exists: the app was specified as "clean and
// light", so there is one look to get right rather than two.

internal val Teal10 = Color(0xFF00201D)
internal val Teal40 = Color(0xFF1F6F6A)
internal val Teal90 = Color(0xFFB6EDE6)
internal val Teal95 = Color(0xFFD6F6F1)

internal val Slate10 = Color(0xFF08201D)
internal val Slate40 = Color(0xFF4B635F)
internal val Slate90 = Color(0xFFCDE8E3)

internal val Indigo10 = Color(0xFF0C1F33)
internal val Indigo40 = Color(0xFF46617A)
internal val Indigo90 = Color(0xFFD3E4F6)

internal val Ink = Color(0xFF1C1B19)
internal val InkMuted = Color(0xFF444B4A)
internal val Paper = Color(0xFFFBFAF8)
internal val PaperRaised = Color(0xFFFFFFFF)
internal val PaperLow = Color(0xFFF5F4F1)
internal val PaperContainer = Color(0xFFEFEFEC)
internal val PaperHigh = Color(0xFFE9E9E6)
internal val PaperHighest = Color(0xFFE3E3E0)
internal val Line = Color(0xFF74797A)
// outlineVariant, so this is every divider and card border in the app. Picked for
// edge visibility against Paper in daylight, not for text contrast: the earlier
// 0xFFC9CECD measured ~1.53:1 on Paper and vanished on a phone outdoors.
internal val LineFaint = Color(0xFF9AA1A0)
internal val SurfaceVariant = Color(0xFFDFE4E2)

internal val Red10 = Color(0xFF410E0B)
internal val Red40 = Color(0xFFB3261E)
internal val Red90 = Color(0xFFF9DEDC)

internal val Amber10 = Color(0xFF2A1800)
internal val Amber90 = Color(0xFFFFDEA8)

internal val Green10 = Color(0xFF0A2110)
internal val Green90 = Color(0xFFCBEBCB)

internal val Grey10 = Color(0xFF16201F)
internal val Grey90 = Color(0xFFDDE4E2)

/**
 * A tint to sit content on, and the ink to write on that tint.
 *
 * Chips and badges throughout the app are "tint plus dark ink", so bundling the
 * pair together stops call sites from pairing a foreground with the wrong
 * background and quietly failing contrast.
 */
data class Accent(val container: Color, val onContainer: Color)

/**
 * Meaning-carrying colours that sit outside the Material scheme.
 *
 * Priorities and log kinds need a stable identity that does not shift when the
 * scheme's roles get reused elsewhere, so they live here as a small named set
 * rather than being pulled from `colorScheme` at each call site.
 */
object DaybookAccents {
    val priorityLow = Accent(Grey90, Grey10)
    val priorityMedium = Accent(Teal90, Teal10)
    val priorityHigh = Accent(Amber90, Amber10)
    val priorityUrgent = Accent(Red90, Red10)

    val kindNote = Accent(Grey90, Grey10)
    val kindDecision = Accent(Indigo90, Indigo10)
    val kindBlocker = Accent(Red90, Red10)
    val kindWin = Accent(Green90, Green10)

    val overdue = Accent(Red90, Red10)
    val done = Accent(Green90, Green10)
    val neutral = Accent(Grey90, Grey10)
}
