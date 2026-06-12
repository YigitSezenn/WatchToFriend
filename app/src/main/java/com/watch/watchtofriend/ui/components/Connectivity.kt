package com.watch.watchtofriend.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.FirebaseBootstrap

private fun ConnectivityManager.hasValidatedInternet(): Boolean {
    val network = activeNetwork ?: return false
    val caps = getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/** İnternet bağlantısı durumunu canlı izleyen Compose durumu. */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val isOnline = remember { mutableStateOf(true) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        isOnline.value = cm?.hasValidatedInternet() == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val online = cm?.hasValidatedInternet() == true
                isOnline.value = online
                if (online) FirebaseBootstrap.onNetworkAvailable()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val wasOnline = isOnline.value
                isOnline.value = online
                if (online && !wasOnline) FirebaseBootstrap.onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                isOnline.value = cm?.hasValidatedInternet() == true
            }
        }
        try { cm?.registerDefaultNetworkCallback(callback) } catch (_: Exception) {}
        onDispose { try { cm?.unregisterNetworkCallback(callback) } catch (_: Exception) {} }
    }
    return isOnline
}

/** Çevrimdışıyken üstte gösterilen ince uyarı şeridi. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            stringResource(R.string.common_offline_banner),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
