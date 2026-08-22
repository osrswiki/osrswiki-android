package com.omiyawaki.osrswiki.network

import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.dataclient.okhttp.OfflineCacheInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Factory for the shared OkHttpClient used by article parse, wiki static/CDN
 * GETs, calculator `/load.php` proxy traffic, and saved-page download/save.
 *
 * HTTP disk cache: [WikiHttpCachePolicy.CACHE_DIR_NAME] under the app
 * `cacheDir`, bounded at [WikiHttpCachePolicy.CACHE_MAX_BYTES] (64 MiB).
 * OkHttp evicts LRU journal entries past that size. See
 * [WikiHttpCachePolicy] for what is cacheable vs must stay fresh.
 *
 * Application interceptor order:
 * 1. [WikiHttpCachePolicyInterceptor] — request Cache-Control
 * 2. [OfflineCacheInterceptor] — persist `X-Offline-Save`; Room fallback on
 *    [java.io.IOException]
 * 3. debug [HttpLoggingInterceptor] at BASIC (never BODY)
 */
object OkHttpClientFactory {

    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    // Lazy initialization for the offline-enabled OkHttpClient
    val offlineClient: OkHttpClient by lazy {
        buildOfflineCapableClient()
    }

    private fun buildOfflineCapableClient(): OkHttpClient {
        val context = OSRSWikiApp.instance.applicationContext
        val appDatabase = AppDatabase.instance

        // Dependencies for OfflineCacheInterceptor
        val offlineObjectDao = appDatabase.offlineObjectDao()
        val offlineCacheInterceptor = OfflineCacheInterceptor(
            context = context,
            offlineObjectDao = offlineObjectDao,
            appDatabase = appDatabase
        )

        val httpCacheDir = File(context.cacheDir, WikiHttpCachePolicy.CACHE_DIR_NAME)
        val builder = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(Cache(httpCacheDir, WikiHttpCachePolicy.CACHE_MAX_BYTES))
            .addInterceptor(WikiHttpCachePolicyInterceptor())
            .addInterceptor(offlineCacheInterceptor) // Unified offline content caching

        // Add other common interceptors, e.g., HttpLoggingInterceptor for debugging
        // Assuming your BuildConfig is accessible, e.g., com.omiyawaki.osrswiki.BuildConfig
        if (com.omiyawaki.osrswiki.BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor()
            // BASIC logs method/URL/status only. BODY would buffer every search and parse
            // payload (including full article HTML) on debug installs and dominate latency.
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
            builder.addInterceptor(loggingInterceptor)
        }
    
        return builder.build()
    }
}
