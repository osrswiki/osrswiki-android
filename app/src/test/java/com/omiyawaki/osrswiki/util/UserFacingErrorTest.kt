package com.omiyawaki.osrswiki.util

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserFacingErrorTest {
    @Test
    fun backendAndSerializationDetailsNeverReachUsers() {
        val raw = IllegalStateException("Field 'query' is required for type with serial name 'GeneratedSearchApiResponse'")
        val shown = UserFacingError.message(raw)
        assertEquals("Something went wrong. Please try again.", shown)
        assertFalse(shown.contains("serial name", ignoreCase = true))
        assertFalse(shown.contains("query", ignoreCase = true))
    }

    @Test
    fun networkFailuresUseActionableNontechnicalCopy() {
        assertEquals("Please check your internet connection and try again.", UserFacingError.message(IOException("socket")))
        assertEquals("That took too long. Please try again.", UserFacingError.message(SocketTimeoutException("timeout")))
    }
}
