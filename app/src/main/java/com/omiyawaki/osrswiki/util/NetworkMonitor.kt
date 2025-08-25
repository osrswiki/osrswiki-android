package com.omiyawaki.osrswiki.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Utility to observe network connectivity status.
 *
 * Usage:
 * 1. Instantiate in your Application class or via DI:
 * val networkMonitor = NetworkMonitor(applicationContext)
 * 2. Access the StateFlow:
 * networkMonitor.isOnline // StateFlow<Boolean>
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Flow that emits true if network is available, false otherwise.
    // It uses callbackFlow to wrap the ConnectivityManager.NetworkCallback.
    val isOnlineFlow: Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true) // Network is available
            }

            override fun onLost(network: Network) {
                trySend(false) // Network is lost
            }

            override fun onUnavailable() {
                trySend(false) // Network is unavailable
            }
        }

        // Get initial state
        val currentNetwork = connectivityManager.activeNetwork
        if (currentNetwork != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(currentNetwork)
            // Only require NET_CAPABILITY_INTERNET, not VALIDATED
            // VALIDATED can fail in emulator environments even when internet works
            trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        } else {
            trySend(false)
        }

        // Register the callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        }

        // Unregister the callback when the flow is cancelled
        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    // Convert the Flow to a StateFlow for easier observation in ViewModels/UI
    // Typically, you'd provide a CoroutineScope from your Application class or DI.
    // Using a default scope here for simplicity in this example.
    val isOnline: StateFlow<Boolean> = isOnlineFlow
        .stateIn(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default), // Or use your app's global scope
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = isCurrentlyConnected(connectivityManager) // Provide an initial value
        )

    private fun isCurrentlyConnected(connectivityManager: ConnectivityManager): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        // Only require NET_CAPABILITY_INTERNET, not VALIDATED
        // VALIDATED can fail in emulator environments even when internet works
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}