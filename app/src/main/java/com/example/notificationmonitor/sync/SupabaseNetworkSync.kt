package com.example.notificationmonitor.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.notificationmonitor.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Sends every notification that has not reached Supabase.
 * While the device is offline the rows stay queued, and a validated
 * network connection flushes them together.
 */
class SupabaseNetworkSync(
    context: Context,
    private val publisher: SupabasePublisher,
    private val repository: NotificationRepository,
    private val scope: CoroutineScope
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val flushRequests = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var flushWhenUnconfirmed = false

    fun start() {
        val manager = connectivity ?: return
        scope.launch {
            for (ignored in flushRequests) {
                drain()
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val capabilities = manager.getNetworkCapabilities(network) ?: return
                    if (hasValidatedInternet(capabilities)) {
                        requestFlush(networkConfirmed = true)
                    }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    if (hasValidatedInternet(networkCapabilities)) {
                        requestFlush(networkConfirmed = true)
                    }
                }
            }
        )
    }

    fun requestFlush(networkConfirmed: Boolean = false) {
        if (networkConfirmed) {
            flushWhenUnconfirmed = true
        }
        flushRequests.trySend(Unit)
    }

    private suspend fun drain() {
        val confirmed = flushWhenUnconfirmed
        flushWhenUnconfirmed = false
        if (!confirmed && !isOnline()) return
        try {
            when (val outcome = publisher.pushPending(
                loadPage = { limit -> repository.unsyncedNotifications(limit) },
                markSynced = { ids -> repository.markSupabaseSynced(ids) }
            )) {
                is SupabaseOutcome.Failure ->
                    Log.w(TAG, "Supabase flush failed status=${outcome.statusCode} ${outcome.message}")
                SupabaseOutcome.Disabled -> Unit
                is SupabaseOutcome.Success -> Unit
            }
        } catch (error: Exception) {
            Log.e(TAG, "Supabase flush failed", error)
        }
    }

    private fun isOnline(): Boolean {
        val manager = connectivity ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return hasValidatedInternet(capabilities)
    }

    private fun hasValidatedInternet(capabilities: NetworkCapabilities): Boolean {
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val TAG = "SupabaseNetworkSync"
    }
}
