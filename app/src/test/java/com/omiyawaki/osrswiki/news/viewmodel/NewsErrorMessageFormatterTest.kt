package com.omiyawaki.osrswiki.news.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class NewsErrorMessageFormatterTest {

    @Test
    fun loadErrorHidesRawHostnamesFromOfflineFailures() {
        val message = NewsErrorMessageFormatter.loadMessage(
            UnknownHostException("Unable to resolve host \"oldschool.runescape.wiki\"")
        )

        assertEquals(
            "Home can’t reach the wiki right now. Your device may be offline.",
            message
        )
        assertFalse(message.contains("oldschool.runescape.wiki"))
        assertFalse(message.contains("Unable to resolve host"))
    }

    @Test
    fun refreshErrorHidesRawExceptionMessages() {
        val message = NewsErrorMessageFormatter.refreshMessage(
            IOException("timeout talking to oldschool.runescape.wiki")
        )

        assertEquals("Home couldn’t refresh from the wiki. Please try again.", message)
        assertFalse(message.contains("oldschool.runescape.wiki"))
        assertFalse(message.contains("timeout"))
    }

    @Test
    fun timeoutMessageDoesNotMisdiagnoseTheDeviceConnection() {
        val message = NewsErrorMessageFormatter.loadMessage(
            java.net.SocketTimeoutException("failed to connect after 10000ms")
        )

        assertEquals("Home couldn’t reach the wiki in time. Please try again.", message)
        assertFalse(message.contains("check your connection", ignoreCase = true))
    }
}
