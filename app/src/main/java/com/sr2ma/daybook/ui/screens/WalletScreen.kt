package com.sr2ma.daybook.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.PassCategory
import com.sr2ma.daybook.ui.DaybookUiState
import com.sr2ma.daybook.ui.DaybookViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Wallet tab: Google Wallet-style passes list matching the official digital wallet layout.
 *
 * Features:
 * - "Search your Wallet" pill search bar
 * - Header with "Passes", sort toggle, refresh, and Add button
 * - Sleek dark pass cards with circular avatar logo, title, location/date subtitle, and favorite star
 * - Dedicated "Archived passes" button
 * - Tapping a pass opens the authentic Google Wallet Entry Pass ticket sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    state: DaybookUiState,
    viewModel: DaybookViewModel,
    userName: String? = null,
    modifier: Modifier = Modifier,
) {
    var viewingPass by remember { mutableStateOf<Pass?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortAlphabetical by remember { mutableStateOf(false) }
    val favoritedPassIds = remember { mutableStateListOf<Long>() }
    var showArchivedDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    if (state.walletScanOpen) {
        CameraScreen(
            onScanResult = viewModel::onScanResult,
            onDismiss = viewModel::closeWalletScanner,
        )
        return
    }

    if (state.passes.isEmpty()) {
        WalletEmptyState(
            onScan = { viewModel.openWalletScanner() },
            onManual = { category -> viewModel.newPassManual(category) },
            modifier = modifier,
        )
    } else {
        // Filter and sort passes
        val filteredPasses = remember(state.passes, searchQuery, sortAlphabetical) {
            var list = if (searchQuery.isBlank()) {
                state.passes
            } else {
                state.passes.filter { pass ->
                    pass.title.contains(searchQuery, ignoreCase = true) ||
                        pass.notes.contains(searchQuery, ignoreCase = true) ||
                        pass.barcodeValue.contains(searchQuery, ignoreCase = true) ||
                        categoryLabelString(pass.category).contains(searchQuery, ignoreCase = true)
                }
            }
            if (sortAlphabetical) {
                list = list.sortedBy { it.title.lowercase() }
            }
            list
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Search your Wallet pill ──────────────────────────────────────
            item(key = "wallet-search") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search your Wallet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.weight(1f),
                        )
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Passes Header Row (Google Wallet style) ──────────────────────
            item(key = "wallet-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Passes",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Sort toggle button
                        FilledTonalIconButton(
                            onClick = { sortAlphabetical = !sortAlphabetical },
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sort),
                                contentDescription = "Sort passes",
                                modifier = Modifier.size(18.dp),
                                tint = if (sortAlphabetical) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Add Button
                        Button(
                            onClick = { showAddSheet = true },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Add", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // ── Pass Items List ──────────────────────────────────────────────
            itemsIndexed(filteredPasses, key = { _, pass -> pass.id }) { index, pass ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(pass.id) {
                    delay(index * 40L)
                    visible = true
                }
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        initialOffsetY = { it / 3 },
                    ) + fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                ) {
                    val isFavorite = favoritedPassIds.contains(pass.id)
                    GoogleWalletPassCard(
                        pass = pass,
                        isFavorite = isFavorite,
                        onFavoriteToggle = {
                            if (isFavorite) favoritedPassIds.remove(pass.id)
                            else favoritedPassIds.add(pass.id)
                        },
                        onClick = { viewingPass = pass },
                    )
                }
            }

            // ── Archived Passes Pill Button ──────────────────────────────────
            item(key = "wallet-archived") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OutlinedButton(
                        onClick = { showArchivedDialog = true },
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_archive),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Archived passes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    if (showArchivedDialog) {
        val expiredPasses = state.passes.filter { it.expiryDate?.isBefore(LocalDate.now()) == true }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showArchivedDialog = false },
            title = { Text("Archived Passes") },
            text = {
                if (expiredPasses.isEmpty()) {
                    Text("No expired or archived passes. All your active passes are shown on the main wallet screen.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${expiredPasses.size} expired pass(es) preserved in secure local storage:")
                        expiredPasses.forEach { p ->
                            Text(
                                text = "• ${p.title} (Expired: ${p.expiryDate})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showArchivedDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
        ) {
            WalletAddSheet(
                onScan = { showAddSheet = false; viewModel.openWalletScanner() },
                onManual = { category -> showAddSheet = false; viewModel.newPassManual(category) },
            )
        }
    }

    // Dedicated read-only Entry Pass view
    viewingPass?.let { pass ->
        EntryPassSheet(
            pass = pass,
            userName = userName,
            onEdit = { p ->
                viewingPass = null
                viewModel.editPass(p)
            },
            onDelete = { p ->
                viewingPass = null
                viewModel.deletePass(p)
            },
            onDismiss = { viewingPass = null },
        )
    }
}

// ── Google Wallet Pass Card (Matches User's Screenshot 2) ───────────────────

@Composable
private fun GoogleWalletPassCard(
    pass: Pass,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val today = LocalDate.now()
    val isExpired = pass.expiryDate?.isBefore(today) == true

    // Dark sleek charcoal / dark slate card container
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E232E), // Authentic dark navy-charcoal surface
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFF2C3240),
        ),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Circle avatar / badge on left (white circular surface with letter or category icon)
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initial = pass.title.trim().take(1).uppercase()
                    if (initial.isNotBlank() && initial[0].isLetterOrDigit()) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E232E),
                        )
                    } else {
                        Icon(
                            painter = painterResource(categoryIcon(pass.category)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF1E232E),
                        )
                    }
                }
            }

            // Middle: Title & Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pass.title.ifBlank { "Pass" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(3.dp))

                // Subtitle: e.g. "DevX • Aug 22, 2026" or "Event ticket • Valid"
                val subtitle = buildString {
                    if (pass.notes.isNotBlank()) {
                        append(pass.notes.take(24))
                        append(" • ")
                    } else {
                        append(categoryLabelString(pass.category))
                        append(" • ")
                    }
                    if (pass.expiryDate != null) {
                        append("${pass.expiryDate.dayOfMonth} ${pass.expiryDate.month.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    } else {
                        append(if (isExpired) "Expired" else "Active")
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8), // Muted slate gray
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Right: Star / Favorite icon button
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = "Favorite",
                    modifier = Modifier.size(22.dp),
                    tint = if (isFavorite) Color(0xFFFFD700) else Color(0xFF94A3B8),
                )
            }
        }
    }
}

// ── Empty state — "Add to Wallet" style selector ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletEmptyState(
    onScan: () -> Unit,
    onManual: (PassCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_wallet),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.wallet_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.wallet_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showAddSheet = true },
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.wallet_add_title))
            }
        }
    }

    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
        ) {
            WalletAddSheet(
                onScan = { showAddSheet = false; onScan() },
                onManual = { category -> showAddSheet = false; onManual(category) },
            )
        }
    }
}

// ── Add to Wallet bottom sheet — matches Google Wallet "Add to Wallet" UI ────

@Composable
fun WalletAddSheet(onScan: () -> Unit, onManual: (PassCategory) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.wallet_add_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        WalletAddRow(
            iconRes = R.drawable.ic_pass_other,
            titleRes = R.string.pass_cat_other,
            subtitleRes = R.string.wallet_add_other_desc,
            onClick = onScan,
        )
        WalletAddRow(
            iconRes = R.drawable.ic_pass_loyalty,
            titleRes = R.string.pass_cat_loyalty_card,
            subtitleRes = R.string.wallet_add_loyalty_desc,
            onClick = { onManual(PassCategory.LOYALTY_CARD) },
        )
        WalletAddRow(
            iconRes = R.drawable.ic_pass_gift,
            titleRes = R.string.pass_cat_gift_card,
            subtitleRes = R.string.wallet_add_gift_desc,
            onClick = { onManual(PassCategory.GIFT_CARD) },
        )
        WalletAddRow(
            iconRes = R.drawable.ic_pass_transport,
            titleRes = R.string.pass_cat_transport,
            subtitleRes = R.string.wallet_add_transport_desc,
            onClick = { onManual(PassCategory.TRANSPORT) },
        )
        WalletAddRow(
            iconRes = R.drawable.ic_pass_id,
            titleRes = R.string.pass_cat_id,
            subtitleRes = R.string.wallet_add_id_desc,
            onClick = { onManual(PassCategory.ID) },
        )
        WalletAddRow(
            iconRes = R.drawable.ic_pass_health,
            titleRes = R.string.pass_cat_health,
            subtitleRes = R.string.wallet_add_health_desc,
            onClick = { onManual(PassCategory.HEALTH) },
        )
    }
}

@Composable
private fun WalletAddRow(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

// ── Category helpers — NO emojis, pure icon references ───────────────────────

@DrawableRes
fun categoryIcon(category: PassCategory): Int = when (category) {
    PassCategory.LOYALTY_CARD -> R.drawable.ic_pass_loyalty
    PassCategory.EVENT_TICKET -> R.drawable.ic_pass_ticket
    PassCategory.TRANSPORT    -> R.drawable.ic_pass_transport
    PassCategory.GIFT_CARD    -> R.drawable.ic_pass_gift
    PassCategory.ID           -> R.drawable.ic_pass_id
    PassCategory.HEALTH       -> R.drawable.ic_pass_health
    PassCategory.OTHER        -> R.drawable.ic_pass_other
}

fun categoryLabelString(category: PassCategory): String = when (category) {
    PassCategory.LOYALTY_CARD -> "Loyalty Card"
    PassCategory.EVENT_TICKET -> "Event Ticket"
    PassCategory.TRANSPORT    -> "Transport Pass"
    PassCategory.GIFT_CARD    -> "Gift Card"
    PassCategory.ID           -> "ID Card"
    PassCategory.HEALTH       -> "Health Pass"
    PassCategory.OTHER        -> "Pass"
}

@Composable
fun categoryLabel(category: PassCategory): String = stringResource(
    when (category) {
        PassCategory.LOYALTY_CARD -> R.string.pass_cat_loyalty_card
        PassCategory.EVENT_TICKET -> R.string.pass_cat_event_ticket
        PassCategory.TRANSPORT    -> R.string.pass_cat_transport
        PassCategory.GIFT_CARD    -> R.string.pass_cat_gift_card
        PassCategory.ID           -> R.string.pass_cat_id
        PassCategory.HEALTH       -> R.string.pass_cat_health
        PassCategory.OTHER        -> R.string.pass_cat_other
    },
)
