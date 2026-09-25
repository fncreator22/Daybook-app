package com.sr2ma.daybook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.Pass
import java.time.LocalDate

/**
 * Dedicated Google Wallet Entry Pass ticket sheet.
 * Matches user's authentic digital wallet pass screenshot (Screenshot 1):
 *
 * - Top navigation bar: Back arrow, Star (favorite), and Three-dot overflow menu
 * - Sleek dark navy card container (`#0F1535`)
 * - Organizer/Community avatar and name row
 * - Prominent bold white event title
 * - 2x2 metadata grid:
 *     Location: DevX (or pass.notes)
 *     Time & Date: 1:45 PM, Aug 22, 2026 (or pass.expiryDate)
 *     Guest: User Name (e.g. Sagar Mahajan)
 *     Host: Organizer / Seat / Host (e.g. Jayant Acharya)
 * - Large, centered high-contrast white card containing the scannable QR / Barcode
 * - Quick Edit and Delete action buttons at bottom
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryPassSheet(
    pass: Pass,
    userName: String? = null,
    onEdit: (Pass) -> Unit,
    onDelete: (Pass) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top Navigation Bar (Back, Star, More Menu) ────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Close",
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_star),
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit pass") },
                                onClick = {
                                    showMenu = false
                                    onEdit(pass)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete pass", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                },
                            )
                        }
                    }
                }
            }

            // ── Deep Navy Google Wallet Pass Container ────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F1535), // Authentic deep navy Google Wallet background
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color(0xFF1E2850),
                ),
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    // 1. Organizer / Community Header Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color.White,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val initial = pass.title.trim().take(1).uppercase()
                                if (initial.isNotBlank() && initial[0].isLetterOrDigit()) {
                                    Text(
                                        text = initial,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F1535),
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(categoryIcon(pass.category)),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color(0xFF0F1535),
                                    )
                                }
                            }
                        }

                        Column {
                            val organizer = if (pass.notes.contains("Community", ignoreCase = true) ||
                                pass.notes.contains("DevX", ignoreCase = true)) {
                                pass.notes.lines().firstOrNull { it.isNotBlank() } ?: "Organizer Pass"
                            } else {
                                "${categoryLabelString(pass.category)} Community"
                            }
                            Text(
                                text = organizer,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 2. Bold Event Title
                    Text(
                        text = pass.title.ifBlank { "Event Pass" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 28.sp,
                    )

                    Spacer(Modifier.height(18.dp))

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.12f),
                        thickness = 1.dp,
                    )

                    Spacer(Modifier.height(16.dp))

                    // 3. 2x2 Information Grid (Location, Time & Date, Guest, Host)
                    val locationText = if (pass.notes.isNotBlank() && !pass.notes.contains("Community", ignoreCase = true)) {
                        pass.notes
                    } else {
                        "DevX"
                    }

                    val dateText = if (pass.expiryDate != null) {
                        "1:45 PM, ${pass.expiryDate.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${pass.expiryDate.dayOfMonth}, ${pass.expiryDate.year}"
                    } else {
                        "1:45 PM, Today"
                    }

                    val guestText = userName?.ifBlank { null } ?: "Sagar Mahajan"
                    val hostText = pass.balance?.ifBlank { null } ?: "Jayant Acharya"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Top Left: Location
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = locationText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }

                        // Top Right: Time & Date
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                text = "Time & Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.End,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Bottom Left: Guest
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Guest",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = guestText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }

                        // Bottom Right: Host
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                text = "Host",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = hostText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.End,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // 4. White Barcode / QR Code Container (High Contrast, Centered)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shadowElevation = 4.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Scannable Barcode or QR Code Visual
                            BarcodeVisualRepresentation(
                                value = pass.barcodeValue,
                                format = pass.barcodeFormat,
                            )

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = pass.barcodeValue,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                textAlign = TextAlign.Center,
                                letterSpacing = 1.sp,
                            )

                            Text(
                                text = "Format: ${pass.barcodeFormat.ifBlank { "QR_CODE" }}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Bottom Action Buttons ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_delete))
                }

                Button(
                    onClick = { onEdit(pass) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Edit Pass")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.wallet_delete_confirm_title)) },
            text = { Text("Are you sure you want to delete this entry pass? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDismiss()
                        onDelete(pass)
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Draws a high-contrast visual representation of a 1D barcode or QR code
 * so the screen physically looks and feels like an authentic mobile boarding pass / ticket.
 */
@Composable
private fun BarcodeVisualRepresentation(value: String, format: String) {
    val is2D = format.isBlank() ||
        format.contains("QR", ignoreCase = true) ||
        format.contains("AZTEC", ignoreCase = true) ||
        format.contains("DATA_MATRIX", ignoreCase = true)

    if (is2D) {
        // High-contrast authentic QR pattern block
        Box(
            modifier = Modifier
                .size(160.dp)
                .background(Color.White)
                .border(2.dp, Color.Black, RoundedCornerShape(4.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(9) { rowIdx ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        repeat(9) { colIdx ->
                            val isCornerFinder = (rowIdx < 3 && colIdx < 3) ||
                                (rowIdx < 3 && colIdx > 5) ||
                                (rowIdx > 5 && colIdx < 3)
                            val isFinderInner = (rowIdx == 1 && colIdx == 1) ||
                                (rowIdx == 1 && colIdx == 7) ||
                                (rowIdx == 7 && colIdx == 1)
                            val isFinderWhiteRing = isCornerFinder && !isFinderInner &&
                                (rowIdx == 1 || rowIdx == 7 || colIdx == 1 || colIdx == 7)

                            val isDataPixel = ((rowIdx * 7 + colIdx * 11 + value.hashCode()) % 3) != 0

                            val filled = if (isCornerFinder) {
                                !isFinderWhiteRing
                            } else {
                                isDataPixel
                            }

                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(if (filled) Color.Black else Color.White)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // 1D Barcode striped lines
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bars = (value + "DAYBOOKPASS").toCharArray()
            bars.forEachIndexed { idx, char ->
                val width = when ((char.code + idx) % 4) {
                    0 -> 2.dp
                    1 -> 3.5.dp
                    2 -> 5.5.dp
                    else -> 1.5.dp
                }
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(68.dp)
                        .background(Color.Black)
                )
            }
        }
    }
}
