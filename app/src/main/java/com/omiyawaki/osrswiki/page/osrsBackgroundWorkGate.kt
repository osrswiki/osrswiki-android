package com.omiyawaki.osrswiki.page

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/** Yields post-load prefetch/preload while the user is actively interacting. */
internal object osrsBackgroundWorkGate {
    @Volatile
    private var pausedUntilElapsed: Long = 0L

    val isPaused: Boolean
        get() = SystemClock.elapsedRealtime() < pausedUntilElapsed

    @JvmStatic
    fun noteUserInteraction(holdMs: Long = 750L) {
        val until = SystemClock.elapsedRealtime() + holdMs
        if (until > pausedUntilElapsed) {
            pausedUntilElapsed = until
        }
    }

    suspend fun waitWhilePaused() {
        while (coroutineContext.isActive && isPaused) {
            delay(16)
        }
    }
}
