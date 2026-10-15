package com.omiyawaki.osrswiki.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.math.min

/**
 * Retries idempotent wiki/prices GETs on 429/503, honoring Retry-After.
 * Sleep is injected so unit tests do not wait on real clocks.
 */
internal class WikiHttpRetryAfterInterceptor(
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val maxRetries: Int = 2,
    private val maxDelayMs: Long = osrsWikiApiStewardship.MAX_RETRY_AFTER_MS
) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        if (!request.method.equals("GET", ignoreCase = true)) {
            return response
        }
        var attempt = 0
        while (
            shouldRetry(response) &&
            attempt < maxRetries
        ) {
            val headerDelay = osrsWikiApiStewardship.retryAfterMillis(
                response.header("Retry-After")
            )
            val delayMs = min(headerDelay ?: (1_000L shl attempt), maxDelayMs)
            response.close()
            if (delayMs > 0) {
                sleeper(delayMs)
            }
            response = chain.proceed(request)
            attempt++
        }
        return response
    }

    private fun shouldRetry(response: Response): Boolean {
        if (osrsWikiApiStewardship.shouldRetryStatus(response.code)) {
            return true
        }
        if (!response.request.url.encodedPath.contains("api.php")) {
            return false
        }
        val prefix = runCatching { response.peekBody(2048).string() }.getOrNull()
        return osrsWikiApiStewardship.shouldRetryMediaWikiError(
            osrsWikiApiStewardship.mediaWikiRetryableErrorCode(prefix)
        )
    }
}
