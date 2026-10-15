package com.omiyawaki.osrswiki.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsWikiApiStewardshipTest {

    @Test
    fun userAgentIdentifiesAppPlatformContactAndSource() {
        val ua = osrsWikiApiStewardship.userAgent("2.1.3", "okhttp")
        assertTrue(ua.startsWith("OSRSWikiApp/2.1.3"))
        assertTrue(ua.contains("Android"))
        assertTrue(ua.contains("okhttp"))
        assertTrue(ua.contains(osrsWikiApiStewardship.CONTACT_EMAIL))
        assertTrue(ua.contains(osrsWikiApiStewardship.ANDROID_SOURCE))
        assertEquals(
            "https://github.com/osrswiki/osrswiki-android",
            osrsWikiApiStewardship.ANDROID_SOURCE
        )
        assertFalse(ua.contains("github.com/omiyawaki/"))
        assertFalse(ua.contains("Java/"))
        assertFalse(ua.contains("Apache-HttpClient"))
    }

    @Test
    fun retryAfterParsesDeltaSecondsAndHttpDate() {
        assertEquals(2_000L, osrsWikiApiStewardship.retryAfterMillis("2"))
        assertEquals(
            osrsWikiApiStewardship.MAX_RETRY_AFTER_MS,
            osrsWikiApiStewardship.retryAfterMillis("120")
        )
        assertNull(osrsWikiApiStewardship.retryAfterMillis(""))
        assertNull(osrsWikiApiStewardship.retryAfterMillis("nope"))

        val now = 1_700_000_000_000L
        val header = "Wed, 15 Nov 2023 12:00:10 GMT"
        val expectedWhen = java.time.ZonedDateTime
            .parse(header, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
        assertEquals(
            (expectedWhen - now).coerceAtLeast(0L).coerceAtMost(osrsWikiApiStewardship.MAX_RETRY_AFTER_MS),
            osrsWikiApiStewardship.retryAfterMillis(header, now)
        )
    }

    @Test
    fun onlyTooManyRequestsAndUnavailableAreRetried() {
        assertTrue(osrsWikiApiStewardship.shouldRetryStatus(429))
        assertTrue(osrsWikiApiStewardship.shouldRetryStatus(503))
        assertFalse(osrsWikiApiStewardship.shouldRetryStatus(500))
        assertFalse(osrsWikiApiStewardship.shouldRetryStatus(404))
    }

    @Test
    fun contactUsesGmailAndRejectsStaleIcloudAddress() {
        assertEquals("osamu+osrswiki@mail.grokbot.com", osrsWikiApiStewardship.CONTACT_EMAIL)
        assertFalse(osrsWikiApiStewardship.userAgent("1.0").contains("icloud.com"))
    }

    @Test
    fun retriesMediaWikiMaxlagAndRatelimitedBodies() {
        val maxlag = """{"error":{"code":"maxlag","info":"Waiting for 10.0.0.1: 5 seconds lagged."}}"""
        val ratelimited = """{"error":{"code":"ratelimited","info":"You've exceeded your rate limit."}}"""
        val parse = """{"parse":{"title":"Varrock","text":"<p>ok</p>"}}"""
        assertEquals("maxlag", osrsWikiApiStewardship.mediaWikiRetryableErrorCode(maxlag))
        assertEquals("ratelimited", osrsWikiApiStewardship.mediaWikiRetryableErrorCode(ratelimited))
        assertNull(osrsWikiApiStewardship.mediaWikiRetryableErrorCode(parse))
        assertTrue(osrsWikiApiStewardship.shouldRetryResponse(200, maxlag))
        assertTrue(osrsWikiApiStewardship.shouldRetryResponse(200, ratelimited))
        assertFalse(osrsWikiApiStewardship.shouldRetryResponse(200, parse))
    }

    @Test
    fun withBackgroundMaxLagAppendsOnce() {
        val url = "https://oldschool.runescape.wiki/api.php?action=query"
        val withLag = osrsWikiApiStewardship.withBackgroundMaxLag(url)
        assertTrue(withLag.contains("maxlag=5"))
        assertEquals(withLag, osrsWikiApiStewardship.withBackgroundMaxLag(withLag))
    }
}
