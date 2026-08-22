package com.omiyawaki.osrswiki.network

import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Request-side policy for the shared wiki OkHttp [okhttp3.Cache].
 *
 * ## Bounds
 * Responses live under `cacheDir/[CACHE_DIR_NAME]` and are capped at
 * [CACHE_MAX_BYTES] (64 MiB). OkHttp evicts least-recently-used journal
 * entries when the directory would exceed that size. This HTTP cache is
 * separate from Room saved-pages (`OfflineCacheInterceptor`) and from
 * `NetworkModuleCache` (`cacheDir/mediawiki_modules`, 50 MiB).
 *
 * ## Cacheable (server `Cache-Control` honored)
 * - GET `action=parse` article HTML (wiki already sends `max-age=300`)
 * - GET `/load.php` ResourceLoader modules (`max-age=300`)
 * - GET wiki-host static (`/images/` and other same-host CDN paths)
 *
 * ## Must stay fresh (`Cache-Control: no-store` on the request)
 * - Search / typeahead: `list=search`, `list=prefixsearch`, `action=opensearch`
 * - Search generators: `generator=search`, `generator=prefixsearch`,
 *   `generator=recentchanges`
 * - Non-wiki hosts, including Cloud Functions (the shared client is reused
 *   by [CloudFunctionRetrofitClient])
 *
 * ## Explicit saves
 * Requests with `X-Offline-Save` use [CacheControl.FORCE_NETWORK] so a live
 * response is persisted to Room. A later offline [java.io.IOException] is
 * still served from Room by [com.omiyawaki.osrswiki.dataclient.okhttp.OfflineCacheInterceptor].
 */
object WikiHttpCachePolicy {
    const val CACHE_DIR_NAME = "okhttp_wiki_http"
    const val CACHE_MAX_BYTES = 64L * 1024L * 1024L

    const val HEADER_OFFLINE_SAVE = "X-Offline-Save"

    private val wikiHosts = setOf(
        "oldschool.runescape.wiki",
        "www.oldschool.runescape.wiki"
    )

    fun isWikiHost(host: String?): Boolean {
        val normalized = host?.lowercase() ?: return false
        return normalized in wikiHosts
    }

    fun mustStayFresh(url: HttpUrl): Boolean {
        if (url.queryParameter("action") == "opensearch") {
            return true
        }
        when (url.queryParameter("list")) {
            "search", "prefixsearch" -> return true
        }
        when (url.queryParameter("generator")) {
            "search", "prefixsearch", "recentchanges" -> return true
        }
        return false
    }

    /**
     * Request cache directive to apply, or null to leave OkHttp / the server
     * in charge (parse, load.php, wiki static).
     */
    fun cacheControlFor(request: Request): CacheControl? {
        if (request.header(HEADER_OFFLINE_SAVE) != null) {
            return CacheControl.FORCE_NETWORK
        }
        if (!isWikiHost(request.url.host)) {
            return CacheControl.Builder().noStore().build()
        }
        if (mustStayFresh(request.url)) {
            return CacheControl.Builder().noStore().build()
        }
        return null
    }
}

internal class WikiHttpCachePolicyInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val cacheControl = WikiHttpCachePolicy.cacheControlFor(original)
            ?: return chain.proceed(original)
        return chain.proceed(
            original.newBuilder()
                .cacheControl(cacheControl)
                .build()
        )
    }
}
