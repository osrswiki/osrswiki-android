package com.omiyawaki.osrswiki.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OkHttpWikiDiskCacheTest {

    private lateinit var cacheDir: File
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("okhttp_wiki_http_test").toFile()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        cacheDir.deleteRecursively()
    }

    @Test
    fun parseLikeGetIsServedFromDiskCacheOnSecondRequest() {
        val client = cachedClient()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Cache-Control", "public, max-age=300, s-maxage=300")
                .addHeader("Content-Type", "application/json")
                .setBody("""{"parse":{"title":"Varrock"}}""")
        )

        val url = server.url("/api.php?action=parse&page=Varrock&maxage=300").toString()
        val first = client.newCall(Request.Builder().url(url).build()).execute()
        assertEquals("""{"parse":{"title":"Varrock"}}""", first.body!!.string())
        first.close()
        assertEquals(1, server.requestCount)

        val second = client.newCall(Request.Builder().url(url).build()).execute()
        assertEquals("""{"parse":{"title":"Varrock"}}""", second.body!!.string())
        assertTrue(second.cacheResponse != null)
        second.close()
        assertEquals("second parse GET must not hit the network", 1, server.requestCount)
    }

    @Test
    fun maxAgeZeroSearchLikeGetIsNotStored() {
        val client = cachedClient()
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Cache-Control", "private, must-revalidate, max-age=0")
                    .addHeader("Content-Type", "application/json")
                    .setBody("""{"query":{"search":[]}}""")
            )
        }
        val url = server.url("/api.php?action=query&list=search&srsearch=glory").toString()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun policyNoStorePreventsCachingEvenWhenServerAllowsIt() {
        val client = OkHttpClient.Builder()
            .cache(Cache(cacheDir, WikiHttpCachePolicy.CACHE_MAX_BYTES))
            .addInterceptor(WikiHttpCachePolicyInterceptor())
            .build()
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Cache-Control", "public, max-age=300")
                    .setBody("fresh")
            )
        }
        // localhost is not a wiki host, so the policy applies no-store.
        val url = server.url("/load.php?modules=jquery&only=scripts").toString()
        client.newCall(Request.Builder().url(url).build()).execute().use { assertEquals("fresh", it.body!!.string()) }
        client.newCall(Request.Builder().url(url).build()).execute().use { assertEquals("fresh", it.body!!.string()) }
        assertEquals(2, server.requestCount)
    }

    private fun cachedClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cache(Cache(cacheDir, WikiHttpCachePolicy.CACHE_MAX_BYTES))
            .build()
    }
}
