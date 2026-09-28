package com.sr2ma.daybook.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sr2ma.daybook.ui.theme.CoralRed
import com.sr2ma.daybook.ui.theme.DarkPillBg

data class OverflowActionItem(
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Reusable floating overflow action menu with glassmorphic styling,
 * 16dp rounded corners, and clear visual hierarchy.
 */
@Composable
fun OverflowActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<OverflowActionItem>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
) {
    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
            modifier = modifier
                .background(
                    color = DarkPillBg.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(16.dp),
                )
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    RoundedCornerShape(16.dp),
                )
                .padding(vertical = 4.dp),
        ) {
            actions.forEach { item ->
                val tintColor = if (item.isDestructive) CoralRed else Color.White
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.label,
                            color = tintColor,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            ),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = item.label,
                            tint = tintColor,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = tintColor,
                        leadingIconColor = tintColor,
                    ),
                    onClick = {
                        onDismissRequest()
                        item.onClick()
                    },
                )
            }
        }
    }
}
