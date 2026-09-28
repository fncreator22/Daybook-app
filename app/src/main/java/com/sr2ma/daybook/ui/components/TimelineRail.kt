package com.sr2ma.daybook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sr2ma.daybook.ui.theme.EmeraldTeal
import com.sr2ma.daybook.ui.theme.TimelineRailLine

/**
 * Visual elements for the connected vertical agenda timeline rail.
 *
 * Implements:
 * - Continuous vertical rail line (2dp width)
 * - Timeline node circles with active state indicators
 * - Active emerald status dot (`#2EC4B6`)
 * - Platform / Event badges (`Video`, `Zoom`, `Studio`, `Meet`, etc.)
 * - Tabular figures duration badges (`30m`, `45m`, `1h`)
 */

@Composable
fun ActiveStatusDot(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    dotColor: Color = EmeraldTeal,
    size: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = if (isActive) dotColor else dotColor.copy(alpha = 0.35f),
                shape = CircleShape,
            )
    )
}

@Composable
fun PlatformBadge(
    platform: String,
    modifier: Modifier = Modifier,
) {
    val clean = platform.trim()
    val (badgeBg, badgeBorder, badgeText) = when {
        clean.contains("zoom", ignoreCase = true) -> Triple(Color(0xFF0F2642), Color(0xFF2D8CFF), Color(0xFF70B4FF))
        clean.contains("studio", ignoreCase = true) -> Triple(Color(0xFF28183B), Color(0xFF9D4EDD), Color(0xFFC77DFF))
        clean.contains("meet", ignoreCase = true) || clean.contains("video", ignoreCase = true) ->
            Triple(Color(0xFF0D2F28), Color(0xFF2EC4B6), Color(0xFF68E0D2))
        else -> Triple(Color(0xFF1E2630), Color(0xFF4A5568), Color(0xFFE2E8F0))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = badgeBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorder.copy(alpha = 0.6f)),
        modifier = modifier,
    ) {
        Text(
            text = clean,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            color = badgeText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun DurationBadge(
    durationText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1F2937),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF374151)),
        modifier = modifier,
    ) {
        Text(
            text = durationText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            ),
            color = Color(0xFFD1D5DB),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun TimelineNodeRail(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    railColor: Color = TimelineRailLine,
    nodeColor: Color = EmeraldTeal,
    nodeRadius: Dp = 5.dp,
    railWidth: Dp = 2.dp,
) {
    Canvas(
        modifier = modifier
            .width(18.dp)
            .fillMaxHeight()
    ) {
        val centerX = size.width / 2f
        val centerY = 24.dp.toPx()
        val nodeRadiusPx = nodeRadius.toPx()
        val railWidthPx = railWidth.toPx()

        // Top line segment
        if (!isFirst) {
            drawLine(
                color = railColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, centerY - nodeRadiusPx),
                strokeWidth = railWidthPx,
            )
        }

        // Central node circle
        drawCircle(
            color = if (isActive) nodeColor else railColor,
            radius = nodeRadiusPx,
            center = Offset(centerX, centerY),
        )

        if (isActive) {
            drawCircle(
                color = nodeColor.copy(alpha = 0.3f),
                radius = nodeRadiusPx + 3.dp.toPx(),
                center = Offset(centerX, centerY),
            )
        }

        // Bottom line segment
        if (!isLast) {
            drawLine(
                color = railColor,
                start = Offset(centerX, centerY + nodeRadiusPx),
                end = Offset(centerX, size.height),
                strokeWidth = railWidthPx,
            )
        }
    }
}

@Composable
fun TimelineItemRow(
    timeLabel: String,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    nodeColor: Color = EmeraldTeal,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Time column with monospace / tabular figures
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            ),
            color = if (isActive) nodeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(56.dp)
                .padding(top = 18.dp, end = 6.dp),
        )

        // Continuous Timeline Rail Node
        TimelineNodeRail(
            isFirst = isFirst,
            isLast = isLast,
            isActive = isActive,
            nodeColor = nodeColor,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // Item Content Block
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, bottom = 8.dp)
        ) {
            content()
        }
    }
}
