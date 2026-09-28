package com.sr2ma.daybook.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tracks network connectivity status and internet access history.
 *
 * Daybook operates 100% offline-first. When network is used (opt-in sync, model download),
 * the timestamp is recorded so the user has full transparency over when internet access occurred.
 */
class NetworkTracker(
    private val context: Context,
    private val syncPrefs: SyncPreferences,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isConnected = MutableStateFlow(checkInitialConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionType = MutableStateFlow(getNetworkTypeName())
    val connectionType: StateFlow<String> = _connectionType.asStateFlow()

    init {
        try {
            connectivityManager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isConnected.value = true
                    _connectionType.value = getNetworkTypeName()
                }

                override fun onLost(network: Network) {
                    _isConnected.value = checkInitialConnectivity()
                    _connectionType.value = getNetworkTypeName()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    _isConnected.value = hasInternet
                    _connectionType.value = getNetworkTypeName()
                }
            })
        } catch (_: Exception) {
            // Safe fallback if permission or system service is constrained
        }
    }

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getNetworkTypeName(): String {
        val cm = connectivityManager ?: return "Offline"
        val active = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(active) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }

    /**
     * Call this whenever Daybook touches the network (Google sync, model download, etc.)
     */
    fun recordInternetAccess() {
        syncPrefs.recordNetworkAccess()
    }

    /** Returns formatted string of the last recorded internet access. */
    fun formattedLastAccess(): String {
        val ts = syncPrefs.lastNetworkAccessAt
        if (ts <= 0L) return "Never (100% Offline)"
        return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ts))
    }
}
