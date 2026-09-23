package com.sr2ma.daybook.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.PassCategory
import com.sr2ma.daybook.ui.DaybookUiState
import com.sr2ma.daybook.ui.DaybookViewModel

/**
 * The Wallet tab: lists all stored passes and shows the scanner when requested.
 */
@Composable
fun WalletScreen(
    state: DaybookUiState,
    viewModel: DaybookViewModel,
    modifier: Modifier = Modifier,
) {
    if (state.walletScanOpen) {
        CameraScreen(
            onScanResult = viewModel::onScanResult,
            onDismiss = viewModel::closeWalletScanner,
        )
        return
    }

    if (state.passes.isEmpty()) {
        WalletEmptyState(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.passes, key = { it.id }) { pass ->
                PassRow(pass = pass, onClick = { viewModel.editPass(pass) })
            }
        }
    }
}

@Composable
private fun WalletEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.wallet_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wallet_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PassRow(pass: Pass, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Category emoji icon
            Text(
                text = categoryEmoji(pass.category),
                style = MaterialTheme.typography.titleLarge,
            )
            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = pass.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // Category label
                Text(
                    text = categoryLabel(pass.category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Barcode preview
                val barcodePreview = pass.barcodeValue.take(20) +
                    if (pass.barcodeValue.length > 20) "\u2026" else ""
                Text(
                    text = stringResource(R.string.pass_barcode_preview, barcodePreview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp),
                )
                // Expiry date if present
                if (pass.expiryDate != null) {
                    val expiryLabel = "${pass.expiryDate.dayOfMonth} " +
                        pass.expiryDate.month.name.lowercase()
                            .replaceFirstChar { it.uppercase() } +
                        " ${pass.expiryDate.year}"
                    Text(
                        text = stringResource(R.string.pass_expires, expiryLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pass.expiryDate.isBefore(java.time.LocalDate.now()))
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else if (pass.ocrText.isNotBlank()) {
                    // OCR text snippet when no expiry set
                    val snippet = pass.ocrText.replace('\n', ' ').take(50) +
                        if (pass.ocrText.length > 50) "\u2026" else ""
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // Balance if present
                if (pass.balance != null) {
                    Text(
                        text = pass.balance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: PassCategory): String = stringResource(
    when (category) {
        PassCategory.LOYALTY_CARD  -> R.string.pass_cat_loyalty_card
        PassCategory.EVENT_TICKET  -> R.string.pass_cat_event_ticket
        PassCategory.TRANSPORT     -> R.string.pass_cat_transport
        PassCategory.GIFT_CARD     -> R.string.pass_cat_gift_card
        PassCategory.ID            -> R.string.pass_cat_id
        PassCategory.HEALTH        -> R.string.pass_cat_health
        PassCategory.OTHER         -> R.string.pass_cat_other
    },
)

private fun categoryEmoji(category: PassCategory): String = when (category) {
    PassCategory.LOYALTY_CARD  -> "\uD83C\uDFAB"  // 🎫
    PassCategory.EVENT_TICKET  -> "\uD83C\uDF9F"  // 🎟
    PassCategory.TRANSPORT     -> "\uD83D\uDE82"  // 🚂
    PassCategory.GIFT_CARD     -> "\uD83C\uDF81"  // 🎁
    PassCategory.ID            -> "\uD83D\uDCB3"  // 💳
    PassCategory.HEALTH        -> "\u2695\uFE0F"  // ⚕️
    PassCategory.OTHER         -> "\uD83D\uDDC2"  // 🗂
}

