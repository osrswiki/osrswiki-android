package com.omiyawaki.osrswiki.network

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsWikiApiStewardshipContractTest {

    @Test
    fun jsoupAndBridgeCallersUseSharedIdentifiedUserAgent() {
        val random = source("random/RandomPageRepository.kt")
        val news = source("news/repository/NewsRepository.kt")
        val bridge = source("page/NativeMapHandler.kt")
        val retrofit = source("network/RetrofitClient.kt")
        assertTrue(random.contains("osrsWikiApiStewardship.appUserAgent(\"random\")"))
        assertTrue(news.contains("osrsWikiApiStewardship.appUserAgent(\"news\")"))
        assertTrue(bridge.contains("osrsWikiApiStewardship.appUserAgent(\"native-bridge\")"))
        assertTrue(bridge.contains("retryAfterMillis"))
        assertTrue(retrofit.contains("OkHttpClientFactory.offlineClient"))
        assertFalse(retrofit.contains("UserAgentInterceptor"))
        assertFalse(random.contains("Mozilla/5.0"))
        assertFalse(bridge.contains("osrswiki-android/1.7"))
        assertFalse(bridge.contains("OSRSWiki-Android"))
        assertTrue(bridge.contains("osrsGeChartPriceCache"))
        assertTrue(bridge.contains("readMemory"))
        val builder = source("page/PageHtmlBuilder.kt")
        val cache = source("network/osrsGeChartPriceCache.kt")
        assertTrue(builder.contains("memoryPrefetchScript"))
        assertTrue(builder.contains("prefetchDiskAsync"))
        assertTrue(cache.contains("__osrsGeChartPrefetch"))
    }

    private fun source(relativePath: String): String {
        return File("src/main/java/com/omiyawaki/osrswiki/$relativePath").readText()
    }
}
