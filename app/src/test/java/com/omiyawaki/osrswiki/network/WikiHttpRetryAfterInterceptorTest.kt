package com.omiyawaki.osrswiki.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WikiHttpRetryAfterInterceptorTest {

    private lateinit var server: MockWebServer
    private val slept = mutableListOf<Long>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        slept.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun retries429HonoringRetryAfterThenReturnsSuccess() {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "2")
                .setBody("slow down")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true}""")
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(WikiHttpUserAgentInterceptor())
            .addInterceptor(WikiHttpRetryAfterInterceptor(sleeper = { slept += it }))
            .build()
        val response = client.newCall(Request.Builder().url(server.url("/api.php")).build()).execute()
        assertEquals(200, response.code)
        assertEquals("""{"ok":true}""", response.body!!.string())
        response.close()
        assertEquals(2, server.requestCount)
        assertEquals(listOf(2_000L), slept)
        assertEquals(
            osrsWikiApiStewardship.appUserAgent("okhttp"),
            server.takeRequest().getHeader("User-Agent")
        )
    }

    @Test
    fun retriesMediaWikiRatelimitedJsonThenReturnsSuccess() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Retry-After", "1")
                .setBody("""{"error":{"code":"ratelimited","info":"slow down"}}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"query":{"pages":[]}}""")
        )
        val client = OkHttpClient.Builder()
            .addInterceptor(WikiHttpRetryAfterInterceptor(sleeper = { slept += it }))
            .build()
        val response = client.newCall(Request.Builder().url(server.url("/api.php")).build()).execute()
        assertEquals(200, response.code)
        assertEquals("""{"query":{"pages":[]}}""", response.body!!.string())
        response.close()
        assertEquals(2, server.requestCount)
        assertEquals(listOf(1_000L), slept)
    }

    @Test
    fun doesNotRetryPost() {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "1"))
        val client = OkHttpClient.Builder()
            .addInterceptor(WikiHttpRetryAfterInterceptor(sleeper = { slept += it }))
            .build()
        val response = client.newCall(
            Request.Builder().url(server.url("/api.php")).post(ByteArray(0).toRequestBody(null)).build()
        ).execute()
        assertEquals(429, response.code)
        response.close()
        assertEquals(1, server.requestCount)
        assertTrue(slept.isEmpty())
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
