package com.sr2ma.daybook.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.PassCategory
import com.sr2ma.daybook.ui.DaybookUiState
import com.sr2ma.daybook.ui.DaybookViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Wallet tab: lists all stored passes with Google Wallet-style card rows.
 * No emojis — all category indicators use Material-style vector icons.
 *
 * Animation: each PassRow slides+fades in with a staggered spring delay,
 * giving the impression of a polished, senior-built list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    state: DaybookUiState,
    viewModel: DaybookViewModel,
    modifier: Modifier = Modifier,
) {
    var viewingPass by remember { mutableStateOf<Pass?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

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
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "wallet-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "My Wallet & Passes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text(
                            text = "${state.passes.size} passes stored offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    androidx.compose.material3.Button(
                        onClick = { showAddSheet = true },
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Add")
                    }
                }
            }

            itemsIndexed(state.passes, key = { _, pass -> pass.id }) { index, pass ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(pass.id) {
                    delay(index * 50L)  // staggered spring entrance
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
                    PassRow(
                        serial = index + 1,
                        pass = pass,
                        onClick = { viewingPass = pass },
                    )
                }
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
                onScan = { showAddSheet = false; viewModel.openWalletScanner() },
                onManual = { category -> showAddSheet = false; viewModel.newPassManual(category) },
            )
        }
    }

    // Dedicated read-only Entry Pass view
    viewingPass?.let { pass ->
        EntryPassSheet(
            pass = pass,
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

// ── Pass row (Google Wallet style with Serial & Status) ──────────────────────

@Composable
private fun PassRow(serial: Int, pass: Pass, onClick: () -> Unit) {
    val today = LocalDate.now()
    val isExpired = pass.expiryDate?.isBefore(today) == true

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Circle icon background with category icon
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = if (isExpired) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(categoryIcon(pass.category)),
                        contentDescription = categoryLabel(pass.category),
                        modifier = Modifier.size(24.dp),
                        tint = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "#$serial",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = pass.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isExpired) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = if (isExpired) "EXPIRED" else "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = if (isExpired) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = categoryLabel(pass.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pass.expiryDate != null) {
                        Text(
                            text = "• ${pass.expiryDate.dayOfMonth} ${pass.expiryDate.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Barcode snippet
                val barcodeLine = pass.barcodeValue.take(16) +
                    if (pass.barcodeValue.length > 16) "\u2026" else ""
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = barcodeLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    if (pass.balance != null) {
                        Text(
                            text = "\u00b7 ${pass.balance}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // "dot dot dot" (•••) action indicator inviting user to tap and view Entry Pass
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text(
                    text = "•••",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(
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
