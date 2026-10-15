package com.omiyawaki.osrswiki.network

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpClientFactoryContractTest {

    @Test
    fun factoryWiresBoundedWikiHttpCacheAndPolicyBeforeOfflineInterceptor() {
        val factory = source("network/OkHttpClientFactory.kt")
        assertTrue(factory.contains("WikiHttpCachePolicy.CACHE_DIR_NAME"))
        assertTrue(factory.contains("WikiHttpCachePolicy.CACHE_MAX_BYTES"))
        assertTrue(factory.contains("WikiHttpCachePolicyInterceptor()"))
        assertTrue(factory.contains("WikiHttpUserAgentInterceptor()"))
        assertTrue(factory.contains("WikiHttpRetryAfterInterceptor()"))
        val policyAdd = factory.indexOf(".addInterceptor(WikiHttpCachePolicyInterceptor())")
        val userAgentAdd = factory.indexOf(".addInterceptor(WikiHttpUserAgentInterceptor())")
        val retryAdd = factory.indexOf(".addInterceptor(WikiHttpRetryAfterInterceptor())")
        val offlineAdd = factory.indexOf(".addInterceptor(offlineCacheInterceptor)")
        assertTrue("policy interceptor must run before OfflineCacheInterceptor", policyAdd in 0 until offlineAdd)
        assertTrue("user-agent interceptor must run before retry", userAgentAdd in policyAdd until retryAdd)
        assertTrue("retry interceptor must run before OfflineCacheInterceptor", retryAdd in userAgentAdd until offlineAdd)
        assertFalse(factory.contains("Level.BODY"))
        assertTrue(factory.contains("Level.BASIC"))
    }

    @Test
    fun appColdStartWarmsEssentialModules() {
        val app = source("OSRSWikiApp.kt")
        assertTrue(app.contains("ModuleCacheWarmer.getInstance"))
        assertTrue(app.contains("warmCacheWithEssentials()"))
    }

    private fun source(relativePath: String): String {
        return File("src/main/java/com/omiyawaki/osrswiki/$relativePath").readText()
    }
}
