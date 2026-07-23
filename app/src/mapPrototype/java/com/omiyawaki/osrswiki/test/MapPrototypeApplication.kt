package com.omiyawaki.osrswiki.test

import android.app.Application
import android.os.SystemClock
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypePerformance
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypePerformanceCollectorImpl
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypeRuntimeInstaller

class MapPrototypeApplication : Application() {
    override fun onCreate() {
        osrsMapPrototypeRuntimeInstaller.install()
        osrsMapPrototypePerformance.install(
            osrsMapPrototypePerformanceCollectorImpl(BuildConfig.MAP_PROTOTYPE_CANDIDATE_ID)
        )
        osrsMapPrototypePerformance.beginSession()
        val startNs = SystemClock.elapsedRealtimeNanos()
        super.onCreate()
        osrsMapPrototypePerformance.markPhase(
            "application_super_complete",
            "duration_ms=${(SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0}"
        )
    }
}
