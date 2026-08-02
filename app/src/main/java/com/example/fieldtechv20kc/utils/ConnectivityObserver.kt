package com.example.fieldtechv20kc.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Observes network connectivity changes and automatically kicks Outbox worker
 * when device transitions from offline to online
 */
class ConnectivityObserver(private val context: Context) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * Observe network connectivity status
     * Emits true when connected, false when disconnected
     */
    fun observe(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)
                trySend(false)
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, capabilities)
                val connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(connected)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        // Send initial state
        trySend(isCurrentlyConnected())
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
    
    /**
     * Check current connectivity state synchronously
     */
    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Start observing connectivity and auto-kick uploads on reconnection
     * Call this from Application.onCreate()
     */
    fun startAutoKickOnReconnect(
        scope: kotlinx.coroutines.CoroutineScope
    ) {
        var wasOffline = !isCurrentlyConnected()
        
        FTLog.i("CONNECTIVITY", "Auto-kick on reconnect started (currently ${if (wasOffline) "offline" else "online"})")
        
        scope.launch {
            observe().collect { isConnected ->
                FTLog.i("CONNECTIVITY", "Network state changed: ${if (isConnected) "ONLINE" else "OFFLINE"}")
                
                if (isConnected && wasOffline) {
                    // Transition from offline → online
                    FTLog.i("CONNECTIVITY", "Network restored! Waiting 2 seconds for stability...")
                    
                    // Wait a bit for network to stabilize
                    kotlinx.coroutines.delay(2000)
                    
                    if (isCurrentlyConnected()) {
                        FTLog.i("CONNECTIVITY", "Network confirmed stable, kicking Outbox worker NOW")
                        OutboxWorkHelpers.kickNow(context)
                    } else {
                        FTLog.w("CONNECTIVITY", "Network lost again during wait, skipping kick")
                    }
                }
                wasOffline = !isConnected
            }
        }
    }
}

