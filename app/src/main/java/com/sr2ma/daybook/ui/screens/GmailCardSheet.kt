package com.sr2ma.daybook.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.SuggestionChip
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
import com.sr2ma.daybook.domain.model.GmailMessage
import com.sr2ma.daybook.sync.GmailSyncEngine
import java.time.LocalDate

/**
 * Dedicated read-only "Email Details" presentation sheet.
 *
 * Shown when tapping an email message card on the Today dashboard.
 * Presents all parsed details (sender, subject, extracted topics, subtopics,
 * location, date/time, full body snippet) with overflow actions (+ Add Task,
 * + Add Meeting, Open Gmail, Dismiss/Remove) without dropping straight into
 * an editable form.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GmailCardSheet(
    message: GmailMessage,
    today: LocalDate,
    onAddTask: (GmailMessage) -> Unit,
    onAddMeeting: (GmailMessage) -> Unit,
    onAddLog: (GmailMessage) -> Unit,
    onAddWallet: (GmailMessage) -> Unit,
    onDismissMessage: (GmailMessage) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMenu by remember { mutableStateOf(false) }

    val topics = remember(message.subject, message.snippet) {
        GmailSyncEngine.extractTopics(message.subject, message.snippet)
    }
    val location = remember(message.subject, message.snippet) {
        GmailSyncEngine.extractLocation(message.subject, message.snippet)
    }
    val (extractedDate, extractedTime) = remember(message.subject, message.snippet, today) {
        GmailSyncEngine.extractDateTime(message.subject, message.snippet, today)
    }
    val isActioned = message.actionedAt != null

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
                    color = when {
                        isActioned -> Color(0xFFE6F4EA)
                        message.suggestedAction != null -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                ) {
                    Text(
                        text = when {
                            isActioned -> "ADDED TO DAYBOOK"
                            message.suggestedAction != null -> "ACTION DETECTED"
                            else -> "EMAIL SUGGESTION"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isActioned -> Color(0xFF137333)
                            message.suggestedAction != null -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
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
                                text = { Text("+ Add as Log Entry") },
                                onClick = {
                                    showMenu = false
                                    onDismiss()
                                    onAddLog(message)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("+ Add to Wallet / Pass") },
                                onClick = {
                                    showMenu = false
                                    onDismiss()
                                    onAddWallet(message)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open in Gmail") },
                                onClick = {
                                    showMenu = false
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://mail.google.com")
                                        setPackage("com.google.android.gm")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from Dashboard", color = MaterialTheme.colorScheme.error) },
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

            // ── Email Presentation Card ──────────────────────────────────────
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
                        text = message.subject.ifBlank { "No Subject" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(16.dp))

                    // Sender
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
                                    painter = painterResource(R.drawable.ic_today),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "From",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = message.sender,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Extracted Date / Time
                    if (extractedDate != null || extractedTime != null) {
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
                                    text = "Detected Schedule",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val datePart = extractedDate?.let { Dates.shortLabel(it, today) } ?: ""
                                val timePart = extractedTime?.let { " at $it" } ?: ""
                                Text(
                                    text = "$datePart$timePart".trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // Extracted Location
                    if (!location.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_meetings),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Location / Platform",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = location,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // Extracted Topics & Subtopics
                    if (topics.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Column {
                            Text(
                                text = "Topics & Tags",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                topics.forEach { topic ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(topic, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                    }

                    // Full Message Snippet / Body
                    if (message.snippet.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "EMAIL BODY & SNIPPET",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = message.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            if (!isActioned) {
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
                        Text("+ Task")
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onAddMeeting(message)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("+ Meeting")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onAddLog(message)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("+ Log")
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onAddWallet(message)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("+ Wallet / Pass")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onDismiss()
                        onDismissMessage(message)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Dismiss Suggestion", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onDismissMessage(message)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Remove from Dashboard")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
