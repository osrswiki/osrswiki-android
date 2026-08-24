package com.omiyawaki.osrswiki.search

import android.os.SystemClock
import com.omiyawaki.osrswiki.util.log.L

/**
 * Tap clock for Home "View more" → first usable updates-list row.
 * Grep: LOAD-MINMAX first_updates_list_visible
 */
object osrsUpdatesListTiming {
    @Volatile
    private var openAtElapsed: Long? = null

    @Volatile
    private var firstVisibleLogged = false

    fun markOpen(restart: Boolean = true) {
        if (!restart && openAtElapsed != null) return
        openAtElapsed = SystemClock.elapsedRealtime()
        firstVisibleLogged = false
        L.d("LOAD-MINMAX updates_list_open")
    }

    fun markFirstVisible(rowCount: Int) {
        val start = openAtElapsed ?: return
        if (firstVisibleLogged) return
        firstVisibleLogged = true
        val elapsed = SystemClock.elapsedRealtime() - start
        L.d("LOAD-MINMAX first_updates_list_visible elapsedMs=$elapsed rowCount=$rowCount")
    }
}
