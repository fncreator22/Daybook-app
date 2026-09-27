package com.sr2ma.daybook.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sr2ma.daybook.R
import com.sr2ma.daybook.domain.model.SyncStatus

/**
 * Non-blocking top banner shown on Meetings/Tasks screens when:
 *   (a) the device is offline, AND
 *   (b) there is at least one item in PENDING_SYNC or SYNC_ERROR state.
 *
 * Tapping the banner opens the system network settings activity so the user
 * can quickly enable connectivity.
 *
 * Per R4: "N items waiting to sync — tap to open network settings"
 */
@Composable
fun PendingSyncBanner(
    pendingCount: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val offline = !isOnline(context)
    val show = offline && pendingCount > 0

    AnimatedVisibility(
        visible = show,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_WIRELESS_SETTINGS
                        )
                    )
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sync),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (pendingCount == 1) {
                        "1 item waiting to sync — tap to open network settings"
                    } else {
                        "$pendingCount items waiting to sync — tap to open network settings"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/**
 * Small sync-error badge shown on individual meeting/task rows when the item
 * has sync_status = SYNC_ERROR. Tapping it triggers a retry.
 */
@Composable
fun SyncErrorBadge(
    syncStatus: SyncStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (syncStatus != SyncStatus.SYNC_ERROR) return

    Icon(
        painter = painterResource(R.drawable.ic_sync_error),
        contentDescription = "Sync error — tap to retry",
        tint = MaterialTheme.colorScheme.error,
        modifier = modifier
            .size(18.dp)
            .clickable { onRetry() },
    )
}

@ReadOnlyComposable
@Composable
private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
