package com.omiyawaki.osrswiki.network

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Cache warming for MediaWiki ResourceLoader modules.
 *
 * Fetches the same `/load.php` query shapes the article WebView will request
 * (calculator sequential inject + common RL script-only URLs) and stores them
 * in [NetworkModuleCache]. ResourceLoader itself is not rewritten; the
 * intercept path serves a HIT when the canonical query matches.
 */
class ModuleCacheWarmer internal constructor(
    context: Context,
    private val clientProvider: () -> OkHttpClient
) {

    companion object {
        private const val TAG = "ModuleCacheWarmer"
        const val BASE_LOAD_URL = "https://oldschool.runescape.wiki/load.php"

        /**
         * Modules the calculator runtime injects as sequential `/load.php`
         * script tags. `mediawiki.widgets` is requested without `only=scripts`,
         * matching `osrs_calculator_runtime.js`.
         */
        val CALCULATOR_INJECT_MODULES: List<EssentialModule> = listOf(
            EssentialModule("jquery", onlyScripts = true),
            EssentialModule("oojs", onlyScripts = true),
            EssentialModule("oojs-ui-core", onlyScripts = true),
            EssentialModule("oojs-ui-widgets", onlyScripts = true),
            EssentialModule("mediawiki.widgets", onlyScripts = false),
            EssentialModule("ext.gadget.rsw-util", onlyScripts = true)
        )

        /**
         * Cold-start essentials: calculator/OOUI inject list plus common RL
         * gadgets. `oojs` is required before `oojs-ui-core` on calculator pages.
         */
        val ESSENTIAL_MODULES: List<String> = listOf(
            "jquery",
            "oojs",
            "mediawiki.base",
            "mediawiki.util",
            "mediawiki.page.ready",
            "ext.gadget.rsw-util",
            "ext.gadget.GECharts",
            "ext.gadget.tooltips",
            "ext.gadget.calc-core",
            "oojs-ui-core",
            "oojs-ui-widgets",
            "mediawiki.widgets"
        )

        @Volatile
        private var INSTANCE: ModuleCacheWarmer? = null

        fun getInstance(context: Context): ModuleCacheWarmer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModuleCacheWarmer(
                    context.applicationContext,
                    Companion::defaultClient
                ).also { INSTANCE = it }
            }
        }

        internal fun createForTest(context: Context, client: OkHttpClient): ModuleCacheWarmer {
            return ModuleCacheWarmer(context.applicationContext) { client }
        }

        fun calculatorShapedUrl(module: String, onlyScripts: Boolean): String {
            val builder = BASE_LOAD_URL.toHttpUrl().newBuilder()
                .addQueryParameter("modules", module)
            if (onlyScripts) {
                builder.addQueryParameter("only", "scripts")
            }
            return builder.build().toString()
        }

        fun resourceLoaderShapedUrl(module: String, onlyScripts: Boolean = true): String {
            val builder = BASE_LOAD_URL.toHttpUrl().newBuilder()
                .addQueryParameter("modules", module)
            if (onlyScripts) {
                builder.addQueryParameter("only", "scripts")
            }
            return builder
                .addQueryParameter("skin", "minerva")
                .addQueryParameter("debug", "false")
                .addQueryParameter("lang", "en-gb")
                .build()
                .toString()
        }

        fun essentialLoadUrls(): List<String> {
            val urls = LinkedHashSet<String>()
            CALCULATOR_INJECT_MODULES.forEach { spec ->
                urls.add(calculatorShapedUrl(spec.name, spec.onlyScripts))
            }
            ESSENTIAL_MODULES.forEach { name ->
                urls.add(resourceLoaderShapedUrl(name, onlyScripts = true))
            }
            urls.add(resourceLoaderShapedUrl("mediawiki.widgets", onlyScripts = false))
            urls.add(calculatorShapedUrl("ext.gadget.calc-core", onlyScripts = true))
            return urls.toList()
        }

        private fun defaultClient(): OkHttpClient {
            return try {
                OkHttpClientFactory.offlineClient
            } catch (_: Exception) {
                OkHttpClient()
            }
        }
    }

    private val moduleCache = NetworkModuleCache.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Warm the cache with modules needed for a page.
     *
     * @param pageModules List of module names from RLPAGEMODULES
     * @param isMobile Whether this is for mobile skin (affects some parameters)
     */
    fun warmCacheForPage(pageModules: List<String>, isMobile: Boolean = true) {
        if (pageModules.isEmpty()) {
            Log.d(TAG, "No modules to warm cache for")
            return
        }
        scope.launch {
            val urls = pageModules.map { name ->
                resourceLoaderShapedUrl(name, onlyScripts = true).let { url ->
                    if (isMobile) {
                        url
                    } else {
                        url.replace("skin=minerva", "skin=vector")
                    }
                }
            }
            warmUrls(urls)
        }
    }

    /**
     * Fetch each URL and store it in [NetworkModuleCache]. Skips URLs already
     * present. Used by tests as a completed warm, and by [warmCacheWithEssentials].
     */
    internal suspend fun warmUrls(urls: Collection<String>) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting cache warming for ${urls.size} load.php URLs")
        for (url in urls) {
            if (moduleCache.isCached(url)) {
                Log.d(TAG, "Already cached: ${moduleNameForLog(url)}")
                continue
            }
            val response = fetchModule(url)
            if (response != null) {
                moduleCache.putResponseSync(url, response)
                Log.d(TAG, "Cached ${moduleNameForLog(url)} (${response.length} bytes)")
            } else {
                Log.w(TAG, "Failed to fetch ${moduleNameForLog(url)}")
            }
        }
        Log.d(TAG, "Cache warming completed")
    }

    private fun fetchModule(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OSRSWiki-Android")
                .header("Accept", "application/javascript, */*")
                .get()
                .build()
            clientProvider().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch module: HTTP ${response.code}")
                    return@use null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching module: ${e.message}")
            null
        }
    }

    /**
     * Warm cache with commonly needed modules.
     * Called from [com.omiyawaki.osrswiki.OSRSWikiApp] after network init.
     */
    fun warmCacheWithEssentials() {
        if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) {
            Log.d(TAG, "Skipping module cache warm under Robolectric")
            return
        }
        Log.d(TAG, "Warming cache with essential modules")
        scope.launch {
            warmEssentialsNow()
        }
    }

    internal suspend fun warmEssentialsNow() {
        warmUrls(essentialLoadUrls())
    }

    /**
     * Get cache warming statistics.
     */
    suspend fun getWarmingStats(): WarmingStats = withContext(Dispatchers.IO) {
        val cacheStats = moduleCache.getCacheStats()
        WarmingStats(
            totalCachedModules = cacheStats.fileCount,
            totalCacheSize = cacheStats.totalSizeMB
        )
    }

    private fun moduleNameForLog(url: String): String {
        return url.substringAfter("modules=").substringBefore("&").ifEmpty { url }
    }
}

data class EssentialModule(
    val name: String,
    val onlyScripts: Boolean
)

/**
 * Cache warming statistics data class.
 */
data class WarmingStats(
    val totalCachedModules: Int,
    val totalCacheSize: Long
)
