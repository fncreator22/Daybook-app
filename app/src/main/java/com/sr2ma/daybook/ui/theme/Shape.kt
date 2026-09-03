package com.sr2ma.daybook.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii, a touch softer than the Material defaults.
 *
 * Cards use [Shapes.medium] and sheets use [Shapes.extraLarge]; chips and small
 * buttons use [Shapes.small] so a row of filter chips reads as one control strip.
 */
val DaybookShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
