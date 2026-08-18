package com.omiyawaki.osrswiki.news.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class NewsRepositoryHomeFeedContractTest {

    @Test
    fun homeFeedRequestsDesktopCompleteMarkup() {
        val field = NewsRepository::class.java.getDeclaredField("WIKI_URL")
        field.isAccessible = true
        val url = field.get(null) as String

        assertTrue(
            "The mobile homepage omits On this day and Popular pages; request desktop-complete markup",
            url.contains("mobileaction=toggle_view_desktop")
        )
    }
}
