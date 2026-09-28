package com.sr2ma.daybook.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.NaturalLanguageParser
import com.sr2ma.daybook.domain.ParsedIntent
import com.sr2ma.daybook.domain.model.WhatsAppMessage
import com.sr2ma.daybook.whatsapp.WhatsAppReplyHelper
import java.time.LocalDate

/**
 * Dedicated read-only "WhatsApp Message Details" presentation sheet.
 *
 * Shown when tapping a WhatsApp message card on the Today dashboard.
 * Presents all parsed details (sender, time, intent, parsed fields, message body)
 * with overflow options (+ Add Task, + Add Meeting, Reply, Dismiss).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppCardSheet(
    message: WhatsAppMessage,
    today: LocalDate,
    onAddTask: (WhatsAppMessage) -> Unit,
    onAddMeeting: (WhatsAppMessage) -> Unit,
    onDismissMessage: (WhatsAppMessage) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMenu by remember { mutableStateOf(false) }

    val parsed = remember(message.message, today) {
        NaturalLanguageParser.parse(message.message, today)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE6F4EA),
                ) {
                    Text(
                        text = "WHATSAPP INCOMING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF137333),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = "More options",
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("+ Add as Task") },
                                onClick = {
                                    showMenu = false
                                    onDismiss()
                                    onAddTask(message)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("+ Add as Meeting") },
                                onClick = {
                                    showMenu = false
                                    onDismiss()
                                    onAddMeeting(message)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reply on WhatsApp") },
                                onClick = {
                                    showMenu = false
                                    WhatsAppReplyHelper.openReply(context, message.sender, "")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Dismiss Message", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDismiss()
                                    onDismissMessage(message)
                                }
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Close",
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Presentation Card ────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(20.dp),
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text(
                        text = "From ${message.sender}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(16.dp))

                    // Detected Action
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(
                                        if (parsed.intent == ParsedIntent.CREATE_MEETING) R.drawable.ic_meetings else R.drawable.ic_tasks
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Detected Intent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val intentLabel = when (parsed.intent) {
                                ParsedIntent.CREATE_MEETING -> "Meeting: ${parsed.meetingTitle ?: message.message.take(40)}"
                                ParsedIntent.CREATE_TASK -> "Task: ${parsed.taskTitle ?: message.message.take(40)}"
                                ParsedIntent.CREATE_LOG -> "Log note"
                                else -> "Incoming Notification"
                            }
                            Text(
                                text = intentLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Schedule / Date
                    if (parsed.dueDate != null || parsed.meetingTime != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_clock),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Scheduled Date & Time",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val datePart = parsed.dueDate?.let { Dates.shortLabel(it, today) } ?: ""
                                val timePart = parsed.meetingTime?.let { " at $it" } ?: ""
                                Text(
                                    text = "$datePart$timePart".trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // Message Text
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "FULL MESSAGE TEXT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onAddTask(message)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+ Add Task")
                }

                Button(
                    onClick = {
                        onDismiss()
                        onAddMeeting(message)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+ Add Meeting")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onDismissMessage(message)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                TextButton(
                    onClick = {
                        WhatsAppReplyHelper.openReply(context, message.sender, "")
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Reply on WhatsApp", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
