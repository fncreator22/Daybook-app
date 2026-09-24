package com.sr2ma.daybook.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vivid, classic glassmorphic card component built with native Compose.
 *
 * Implements:
 * - Translucent frosted glass background
 * - Specular border highlight with gradient refraction
 * - Subtle animated ambient light sweep for a living, vivid appearance
 * - Soft shadow depth
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 2.dp,
    animatedSheen: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    // Animated light beam sweep for a living, glassy sheen
    val shimmerOffset by if (animatedSheen) {
        val transition = rememberInfiniteTransition(label = "glass_shimmer")
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmer_sweep",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    // Frosted glass gradient (translucent white + subtle primary tint)
    val glassBgBrush = Brush.linearGradient(
        colors = listOf(
            tint.copy(alpha = 0.82f),
            tint.copy(alpha = 0.65f),
            primaryColor.copy(alpha = 0.08f),
        ),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    // Specular border highlight with animated sweep
    val borderBrush = if (animatedSheen) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.75f),
                primaryColor.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.40f),
            ),
            start = Offset(shimmerOffset * 500f, 0f),
            end = Offset((shimmerOffset + 1f) * 500f, 500f),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.70f),
                Color.White.copy(alpha = 0.25f),
            ),
        )
    }

    val cardModifier = modifier
        .fillMaxWidth()
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.06f),
            spotColor = primaryColor.copy(alpha = 0.12f),
        )
        .clip(shape)
        .background(glassBgBrush)
        .border(BorderStroke(borderWidth, borderBrush), shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(),
                    onClick = onClick,
                )
            } else {
                Modifier
            }
        )

    Box(modifier = cardModifier) {
        content()
    }
}
