package com.omiyawaki.osrswiki.network

import com.omiyawaki.osrswiki.BuildConfig
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Identified User-Agent and Retry-After policy for wiki / prices HTTP.
 *
 * The OSRS Wiki asks automated clients to send a descriptive User-Agent with
 * contact details, honor HTTP caching, use prices API v2, and back off on
 * 429/503. See https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices
 */
object osrsWikiApiStewardship {
    const val CONTACT_EMAIL = "osamu+osrswiki@mail.grokbot.com"
    const val ANDROID_SOURCE = "https://github.com/osrswiki/osrswiki-android"
    const val MAX_RETRY_AFTER_MS = 30_000L
    const val BACKGROUND_MAXLAG_SECONDS = 5
    const val GE_CHART_CACHE_TTL_MS = 30L * 60L * 1_000L
    const val SEARCH_QUERY_CACHE_SIZE = 16
    const val MEDIAWIKI_ERROR_MAXLAG = "maxlag"
    const val MEDIAWIKI_ERROR_RATELIMITED = "ratelimited"

    fun userAgent(
        versionName: String,
        component: String = "app"
    ): String {
        val version = versionName.ifBlank { "dev" }
        val label = component.ifBlank { "app" }
        return "OSRSWikiApp/$version (Android; $label; $CONTACT_EMAIL; +$ANDROID_SOURCE)"
    }

    fun appUserAgent(component: String = "app"): String =
        userAgent(BuildConfig.VERSION_NAME, component)

    fun shouldRetryStatus(code: Int): Boolean = code == 429 || code == 503

    fun shouldRetryMediaWikiError(code: String?): Boolean =
        code == MEDIAWIKI_ERROR_MAXLAG || code == MEDIAWIKI_ERROR_RATELIMITED

    fun mediaWikiRetryableErrorCode(body: String?): String? {
        val text = body?.trim().orEmpty()
        if (text.isEmpty() || !text.contains("\"error\"")) {
            return null
        }
        val code = MEDIAWIKI_ERROR_CODE.find(text)?.groupValues?.getOrNull(1)
        return code?.takeIf { shouldRetryMediaWikiError(it) }
    }

    fun shouldRetryResponse(statusCode: Int, bodyPrefix: String?): Boolean =
        shouldRetryStatus(statusCode) || shouldRetryMediaWikiError(mediaWikiRetryableErrorCode(bodyPrefix))

    fun withBackgroundMaxLag(url: String): String {
        if (url.contains("maxlag=")) {
            return url
        }
        val separator = if (url.contains('?')) '&' else '?'
        return "$url${separator}maxlag=$BACKGROUND_MAXLAG_SECONDS"
    }

    fun retryAfterMillis(
        header: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Long? {
        val raw = header?.trim().orEmpty()
        if (raw.isEmpty()) return null
        raw.toLongOrNull()?.let { seconds ->
            if (seconds < 0) return null
            return (seconds * 1000L).coerceAtMost(MAX_RETRY_AFTER_MS)
        }
        return try {
            val whenMs = ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
            (whenMs - nowMs).coerceAtLeast(0L).coerceAtMost(MAX_RETRY_AFTER_MS)
        } catch (_: Exception) {
            null
        }
    }

    private val MEDIAWIKI_ERROR_CODE = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"")
}
