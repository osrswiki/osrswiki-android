package com.omiyawaki.osrswiki.util.log

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A centralized logging utility to ensure consistent tagging and timestamping.
 * This helps in filtering and analyzing logs for specific features like page loading.
 */
object L {
    private const val TAG = "PageLoadTrace"
    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(message: String) {
        Log.d(TAG, "${sdf.format(Date())} - $message")
    }

    fun w(message: String) {
        Log.w(TAG, "${sdf.format(Date())} - $message")
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, "${sdf.format(Date())} - $message", throwable)
    }
}
