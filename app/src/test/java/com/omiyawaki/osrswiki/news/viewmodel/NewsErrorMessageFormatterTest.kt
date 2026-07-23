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

        assertEquals("Failed to load Home. Please check your connection and try again.", message)
        assertFalse(message.contains("oldschool.runescape.wiki"))
        assertFalse(message.contains("Unable to resolve host"))
    }

    @Test
    fun refreshErrorHidesRawExceptionMessages() {
        val message = NewsErrorMessageFormatter.refreshMessage(
            IOException("timeout talking to oldschool.runescape.wiki")
        )

        assertEquals("Failed to refresh Home. Please check your connection and try again.", message)
        assertFalse(message.contains("oldschool.runescape.wiki"))
        assertFalse(message.contains("timeout"))
    }
}
