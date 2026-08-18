package com.omiyawaki.osrswiki.util

import java.io.IOException
import java.net.SocketTimeoutException

/** Keeps exception types, schema names, URLs, and backend payloads out of release UI. */
object UserFacingError {
    fun message(error: Throwable, fallback: String = "Something went wrong. Please try again."): String = when (error) {
        is SocketTimeoutException -> "That took too long. Please try again."
        is IOException -> "Please check your internet connection and try again."
        else -> fallback
    }
}
