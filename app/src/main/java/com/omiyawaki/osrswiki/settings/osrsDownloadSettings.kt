package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class osrsSavedPageUpdatePolicy(val persistedValue: String) {
    AUTOMATIC("automatic"),
    ON_ACCESS("onAccess"),
    MANUAL("manual");

    companion object {
        fun fromPersisted(value: String?): osrsSavedPageUpdatePolicy {
            return entries.firstOrNull { it.persistedValue == value } ?: ON_ACCESS
        }
    }
}

enum class osrsSavedPageDownloadNetwork(val persistedValue: String) {
    WIFI_ONLY("wifiOnly"),
    ANY("any");

    companion object {
        fun fromPersisted(value: String?): osrsSavedPageDownloadNetwork {
            return entries.firstOrNull { it.persistedValue == value } ?: WIFI_ONLY
        }
    }
}

enum class osrsSavedPageUpdateTrigger {
    AUTOMATIC_SCAN,
    ACCESS,
    MANUAL
}

data class osrsDownloadSettings(
    val updatePolicy: osrsSavedPageUpdatePolicy,
    val downloadNetwork: osrsSavedPageDownloadNetwork
) {
    fun allowsNetwork(isOnline: Boolean, isUnmetered: Boolean): Boolean {
        if (!isOnline) {
            return false
        }
        return when (downloadNetwork) {
            osrsSavedPageDownloadNetwork.ANY -> true
            osrsSavedPageDownloadNetwork.WIFI_ONLY -> isUnmetered
        }
    }

    fun shouldRefreshSnapshot(
        trigger: osrsSavedPageUpdateTrigger,
        isOnline: Boolean,
        isUnmetered: Boolean
    ): Boolean {
        return when (trigger) {
            osrsSavedPageUpdateTrigger.MANUAL -> allowsNetwork(isOnline, isUnmetered)
            osrsSavedPageUpdateTrigger.ACCESS ->
                updatePolicy != osrsSavedPageUpdatePolicy.MANUAL &&
                    allowsNetwork(isOnline, isUnmetered)
            osrsSavedPageUpdateTrigger.AUTOMATIC_SCAN ->
                updatePolicy == osrsSavedPageUpdatePolicy.AUTOMATIC &&
                    allowsNetwork(isOnline, isUnmetered)
        }
    }

    companion object {
        const val KEY_UPDATE_POLICY = "osrsSavedPageUpdatePolicy"
        const val KEY_DOWNLOAD_NETWORK = "osrsSavedPageDownloadNetwork"

        fun load(): osrsDownloadSettings {
            return osrsDownloadSettings(
                updatePolicy = osrsSavedPageUpdatePolicy.fromPersisted(
                    PrefsIoUtil.getString(KEY_UPDATE_POLICY, osrsSavedPageUpdatePolicy.ON_ACCESS.persistedValue)
                ),
                downloadNetwork = osrsSavedPageDownloadNetwork.fromPersisted(
                    PrefsIoUtil.getString(
                        KEY_DOWNLOAD_NETWORK,
                        osrsSavedPageDownloadNetwork.WIFI_ONLY.persistedValue
                    )
                )
            )
        }

        fun isOnline(context: Context): Boolean {
            val capabilities = networkCapabilities(context) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun isUnmetered(context: Context): Boolean {
            val capabilities = networkCapabilities(context) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }

        private fun networkCapabilities(context: Context): NetworkCapabilities? {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        }
    }
}
