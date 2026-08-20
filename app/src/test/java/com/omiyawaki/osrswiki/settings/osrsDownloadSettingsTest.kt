package com.omiyawaki.osrswiki.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsDownloadSettingsTest {
    @Test
    fun onAccessRefreshesOverWifiAndSkipsManualOnly() {
        val onAccessWifi = osrsDownloadSettings(
            updatePolicy = osrsSavedPageUpdatePolicy.ON_ACCESS,
            downloadNetwork = osrsSavedPageDownloadNetwork.WIFI_ONLY
        )
        assertTrue(
            onAccessWifi.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.ACCESS,
                isOnline = true,
                isUnmetered = true
            )
        )
        assertFalse(
            onAccessWifi.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.ACCESS,
                isOnline = true,
                isUnmetered = false
            )
        )
        assertFalse(
            onAccessWifi.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.AUTOMATIC_SCAN,
                isOnline = true,
                isUnmetered = true
            )
        )
    }

    @Test
    fun automaticAllowsBackgroundScanAndAccessRefresh() {
        val automaticAny = osrsDownloadSettings(
            updatePolicy = osrsSavedPageUpdatePolicy.AUTOMATIC,
            downloadNetwork = osrsSavedPageDownloadNetwork.ANY
        )
        assertTrue(
            automaticAny.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.AUTOMATIC_SCAN,
                isOnline = true,
                isUnmetered = false
            )
        )
        assertTrue(
            automaticAny.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.ACCESS,
                isOnline = true,
                isUnmetered = false
            )
        )
        assertFalse(
            automaticAny.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.AUTOMATIC_SCAN,
                isOnline = false,
                isUnmetered = true
            )
        )
    }

    @Test
    fun manualOnlyRefreshesWhenTheUserAsks() {
        val manualWifi = osrsDownloadSettings(
            updatePolicy = osrsSavedPageUpdatePolicy.MANUAL,
            downloadNetwork = osrsSavedPageDownloadNetwork.WIFI_ONLY
        )
        assertTrue(
            manualWifi.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.MANUAL,
                isOnline = true,
                isUnmetered = true
            )
        )
        assertFalse(
            manualWifi.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.ACCESS,
                isOnline = true,
                isUnmetered = true
            )
        )
        assertFalse(
            manualWifi.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.MANUAL,
                isOnline = true,
                isUnmetered = false
            )
        )
    }
}
