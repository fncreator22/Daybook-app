package com.sr2ma.daybook.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.ConversationMessage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * A back-and-forth chat sheet between the user and the on-device agent.
 *
 * The full conversation lives in RAM only. When the sheet is dismissed,
 * [onDismiss] is called and the ViewModel runs [ConversationMemoryEngine.extract]
 * to persist only the signal (entities, preferences, one-line summary) — never
 * the full chat text.
 *
 * Opening gesture: long-press on the mic FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationSheet(
    messages: List<ConversationMessage>,
    agentThinking: Boolean,
    onSend: (String) -> Unit,
    onSuggestionTap: (String) -> Unit,
    onDismiss: () -> Unit,
    onVoiceTap: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Scroll to bottom whenever a new message arrives.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(0.85f),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Text(
                text = "Agent",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
            )

            // ── Message list or empty state ───────────────────────────────────
            if (messages.isEmpty() && !agentThinking) {
                // Empty state — centred hint to get the user started
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.conversation_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.conversation_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.timestampMs }) { msg ->
                        ConversationBubble(msg = msg, onSuggestionTap = onSuggestionTap)
                    }
                }
            }

            // ── Thinking indicator ────────────────────────────────────────────
            if (agentThinking) {
                AgentThinkingIndicator()
            }

            // ── Input ─────────────────────────────────────────────────────────
            ConversationInputRow(onSend = onSend, onVoiceTap = onVoiceTap)
        }
    }
}

// ── ConversationBubble ────────────────────────────────────────────────────────

@Composable
private fun ConversationBubble(
    msg: ConversationMessage,
    onSuggestionTap: (String) -> Unit,
) {
    val isUser = msg.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            val bubbleShape = if (isUser) {
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
            } else {
                RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            }

            val bubbleBrush = if (isUser) {
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    )
                )
            }

            val borderBrush = if (isUser) {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.Transparent,
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.40f),
                    )
                )
            }

            Box(
                modifier = Modifier
                    .shadow(
                        elevation = if (isUser) 2.dp else 1.dp,
                        shape = bubbleShape,
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    )
                    .clip(bubbleShape)
                    .background(bubbleBrush)
                    .border(1.dp, borderBrush, bubbleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (isUser) {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    TypewriterText(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Action chips below agent bubble
            if (!isUser && msg.suggestions.isNotEmpty()) {
                SuggestionChipRow(
                    suggestions = msg.suggestions,
                    onTap = onSuggestionTap,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ── TypewriterText ────────────────────────────────────────────────────────────

@Composable
private fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    delayMs: Long = 20L,
) {
    var displayed by remember(text) { mutableStateOf("") }
    LaunchedEffect(text) {
        text.indices.forEach { i ->
            displayed = text.substring(0, i + 1)
            delay(delayMs)
        }
    }
    Text(text = displayed, modifier = modifier, style = style, color = color)
}

// ── AgentThinkingIndicator ────────────────────────────────────────────────────

@Composable
private fun AgentThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")

    @Composable
    fun dot(delayMs: Int): Float {
        val offset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, delayMillis = delayMs),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot_$delayMs",
        )
        return offset
    }

    val o1 = dot(0)
    val o2 = dot(150)
    val o3 = dot(300)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(o1, o2, o3).forEach { offset ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .offset { IntOffset(0, offset.roundToInt()) }
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

// ── SuggestionChipRow ─────────────────────────────────────────────────────────

@Composable
private fun SuggestionChipRow(
    suggestions: List<String>,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { label ->
            SuggestionChip(
                onClick = { onTap(label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

// ── ConversationInputRow ──────────────────────────────────────────────────────

@Composable
private fun ConversationInputRow(
    onSend: (String) -> Unit,
    onVoiceTap: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .padding(bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Mic button — only shown when voice callback is provided
        if (onVoiceTap != null) {
            IconButton(onClick = onVoiceTap) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = "Voice input",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Type or speak...") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                imeAction = androidx.compose.ui.text.input.ImeAction.Send,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSend = {
                    val trimmed = text.trim()
                    if (trimmed.isNotBlank()) {
                        onSend(trimmed)
                        text = ""
                    }
                }
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                val trimmed = text.trim()
                if (trimmed.isNotBlank()) {
                    onSend(trimmed)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}
