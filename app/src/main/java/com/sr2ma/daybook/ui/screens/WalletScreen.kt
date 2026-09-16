package com.sr2ma.daybook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = pass.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = categoryLabel(pass.category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pass.balance != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = pass.balance, style = MaterialTheme.typography.bodySmall)
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
