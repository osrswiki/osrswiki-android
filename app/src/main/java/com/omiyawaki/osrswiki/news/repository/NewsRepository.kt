package com.omiyawaki.osrswiki.news.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.news.model.*
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Repository for fetching and parsing news feed content from the OSRS Wiki.
 * Enhanced with robust caching capabilities for optimal user experience.
 * Matches iOS NewsRepository feature set for cross-platform parity.
 */
object NewsRepository : NewsFeedRepository {
    private const val BASE_URL = "https://oldschool.runescape.wiki"
    // MobileFrontend strips the desktop-only On this day and Popular pages
    // modules. Request the complete homepage because the Home feed presents
    // those modules as native app sections.
    private const val WIKI_URL = "$BASE_URL/?mobileaction=toggle_view_desktop"
    
    // Cache configuration
    private const val CACHE_PREFS_NAME = "osrs_wiki_feed_cache"
    private const val CACHE_KEY = "wiki_feed_data_desktop_complete_v2"
    private const val CACHE_TIMESTAMP_KEY = "wiki_feed_timestamp_desktop_complete_v2"
    private const val CACHE_REFRESH_ATTEMPT_KEY = "wiki_feed_last_refresh_attempt"
    private const val CACHE_TTL_HOURS = 24L // 24 hours cache TTL like iOS
    private const val REQUEST_TIMEOUT_MILLIS = 10_000
    private const val REQUEST_ATTEMPTS = 2
    private const val REQUEST_RETRY_DELAY_MILLIS = 250L
    
    private val gson = Gson()
    private lateinit var cachePrefs: SharedPreferences
    
    // In-memory cache
    @Volatile
    private var cachedFeed: WikiFeed? = null
    @Volatile 
    private var cacheTimestamp: Long? = null
    @Volatile
    private var lastRefreshAttemptTimestamp: Long? = null
    
    override fun initialize(context: Context) {
        if (!::cachePrefs.isInitialized) {
            cachePrefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
            loadCachedDataFromPrefs()
        }
    }
    
    private fun loadCachedDataFromPrefs() {
        try {
            val cachedJson = cachePrefs.getString(CACHE_KEY, null)
            val timestamp = cachePrefs.getLong(CACHE_TIMESTAMP_KEY, -1L)
            val refreshAttempt = cachePrefs.getLong(CACHE_REFRESH_ATTEMPT_KEY, -1L)
            
            if (cachedJson != null && timestamp != -1L) {
                val feed = gson.fromJson(cachedJson, WikiFeed::class.java)
                cachedFeed = feed
                cacheTimestamp = timestamp
                if (isCacheValid(timestamp)) {
                    L.d("📦 NewsRepository: Loaded valid cached data from SharedPreferences")
                } else {
                    L.d("📦 NewsRepository: Loaded stale cached data for offline fallback")
                }
            }
            
            if (refreshAttempt != -1L) {
                lastRefreshAttemptTimestamp = refreshAttempt
            }
        } catch (e: JsonSyntaxException) {
            L.e("❌ NewsRepository: Failed to parse cached data", e)
            clearCache()
        }
    }
    
    private fun isCacheValid(timestamp: Long): Boolean {
        val currentTime = System.currentTimeMillis()
        val cacheAgeMs = currentTime - timestamp
        val cacheAgeHours = TimeUnit.MILLISECONDS.toHours(cacheAgeMs)
        return cacheAgeHours < CACHE_TTL_HOURS
    }
    
    /**
     * Get cached feed synchronously if available and valid
     */
    override fun getCachedFeedSynchronously(): WikiFeed? {
        return if (cacheTimestamp?.let { isCacheValid(it) } == true) {
            L.d("📦 NewsRepository: Returning valid cached feed")
            cachedFeed
        } else {
            L.d("📦 NewsRepository: No valid cached feed available")
            null
        }
    }
    
    /**
     * Check if current cache is valid
     */
    override val isCacheValid: Boolean
        get() = getCachedFeedSynchronously() != null
    
    /**
     * Get last updated timestamp for UI display
     */
    val lastUpdatedTimestamp: Long?
        get() = cacheTimestamp
    
    /**
     * Format last updated time for user display (matches iOS implementation)
     */
    override fun getLastUpdatedString(): String {
        val currentTime = System.currentTimeMillis()
        
        // If there was a recent refresh attempt (within 2 minutes), show "Just now"
        lastRefreshAttemptTimestamp?.let { lastAttempt ->
            if (currentTime - lastAttempt < TimeUnit.MINUTES.toMillis(2)) {
                return "Just now"
            }
        }
        
        // Otherwise show actual cache age
        val timestamp = cacheTimestamp ?: return "Never updated"
        val ageMs = currentTime - timestamp
        
        return when {
            ageMs < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            ageMs < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(ageMs)
                "${minutes} minute${if (minutes == 1L) "" else "s"} ago"
            }
            ageMs < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(ageMs)
                "${hours} hour${if (hours == 1L) "" else "s"} ago"
            }
            else -> {
                val days = TimeUnit.MILLISECONDS.toDays(ageMs)
                "${days} day${if (days == 1L) "" else "s"} ago"
            }
        }
    }
    
    /**
     * Mark that a refresh attempt was made (regardless of success)
     */
    override fun markRefreshAttempt() {
        lastRefreshAttemptTimestamp = System.currentTimeMillis()
        cachePrefs.edit()
            .putLong(CACHE_REFRESH_ATTEMPT_KEY, lastRefreshAttemptTimestamp!!)
            .apply()
        L.d("🔄 NewsRepository: Marked refresh attempt")
    }
    
    /**
     * Fetches the wiki main page with intelligent caching
     */
    override suspend fun getWikiFeed(forceRefresh: Boolean): Result<WikiFeed> = withContext(Dispatchers.IO) {
        // Initialize if not done yet
        if (!::cachePrefs.isInitialized) {
            val context = OSRSWikiApp.instance?.applicationContext
            context?.let { initialize(it) }
        }
        
        // Check cache first unless forced refresh
        if (!forceRefresh) {
            getCachedFeedSynchronously()?.let { cached ->
                L.d("📦 NewsRepository: Using cached feed data")
                return@withContext Result.success(cached)
            }
        }
        
        L.d("🌐 NewsRepository: Fetching fresh feed data...")
        try {
            val doc = osrsFetchWithRetry(
                maxAttempts = REQUEST_ATTEMPTS,
                retryDelayMillis = REQUEST_RETRY_DELAY_MILLIS
            ) { attempt ->
                L.d("🌐 NewsRepository: Wiki request attempt $attempt/$REQUEST_ATTEMPTS")
                Jsoup.connect(WIKI_URL)
                    .timeout(REQUEST_TIMEOUT_MILLIS)
                    .userAgent("OSRS Wiki Android/${com.omiyawaki.osrswiki.BuildConfig.VERSION_NAME}")
                    .get()
            }
            val feed = WikiFeed(
                recentUpdates = parseRecentUpdates(doc),
                announcements = parseAnnouncements(doc),
                onThisDay = parseOnThisDay(doc),
                popularPages = parsePopularPages(doc)
            )
            
            // Cache the fresh data
            cacheFeed(feed)
            
            Result.success(feed)
        } catch (e: IOException) {
            L.e("❌ NewsRepository: Network error fetching wiki feed", e)
            if (!forceRefresh) {
                cachedFeed?.let { staleFeed ->
                    L.d("📦 NewsRepository: Serving stale Home feed after network failure")
                    return@withContext Result.success(staleFeed)
                }
            }
            Result.failure(e)
        } catch (e: Exception) {
            L.e("❌ NewsRepository: Unexpected error fetching wiki feed", e)
            Result.failure(e)
        }
    }
    
    private fun cacheFeed(feed: WikiFeed) {
        val currentTime = System.currentTimeMillis()
        cachedFeed = feed
        cacheTimestamp = currentTime
        
        try {
            val feedJson = gson.toJson(feed)
            cachePrefs.edit()
                .putString(CACHE_KEY, feedJson)
                .putLong(CACHE_TIMESTAMP_KEY, currentTime)
                .apply()
            L.d("💾 NewsRepository: Cached feed data to SharedPreferences")
        } catch (e: Exception) {
            L.e("❌ NewsRepository: Failed to cache data", e)
        }
    }
    
    /**
     * Clear all cached data
     */
    fun clearCache() {
        cachedFeed = null
        cacheTimestamp = null
        lastRefreshAttemptTimestamp = null
        
        if (::cachePrefs.isInitialized) {
            cachePrefs.edit().clear().apply()
        }
        
        L.d("🗑️ NewsRepository: Cache cleared")
    }
    
    /**
     * Legacy method for backwards compatibility
     */
    suspend fun getWikiFeed(): Result<WikiFeed> = getWikiFeed(forceRefresh = false)

    private fun parseRecentUpdates(doc: Element): List<UpdateItem> {
        val updatesContainer = doc.selectFirst("div.mainpage-recent-updates") ?: return emptyList()
        return updatesContainer.select("div.tile-halves").mapNotNull { tile ->
            // Correctly select the link from the bottom part of the card for text content.
            val textLinkElement = tile.selectFirst("div.tile-bottom a") ?: return@mapNotNull null
            val imageElement = tile.selectFirst("div.tile-top img")

            // The title is in an <h2> tag within the text link.
            val title = textLinkElement.selectFirst("h2")?.text() ?: "No title"

            // The snippet is the LAST <p> tag within the text link.
            val snippet = textLinkElement.select("p").last()?.text() ?: ""

            UpdateItem(
                title = title,
                snippet = snippet,
                imageUrl = imageElement?.attr("src")?.let { "$BASE_URL$it" } ?: "",
                articleUrl = textLinkElement.attr("href").let { "$BASE_URL$it" }
            )
        }
    }

    private fun parseAnnouncements(doc: Element): List<AnnouncementItem> {
        val announcementsContainer = doc.selectFirst("div.mainpage-wikinews dl") ?: return emptyList()
        val dates = announcementsContainer.select("dt")
        val contents = announcementsContainer.select("dd")
        return dates.zip(contents).map { (date, content) ->
            AnnouncementItem(date = date.text(), content = content.html())
        }
    }

    private fun parseOnThisDay(doc: Element): OnThisDayItem? {
        val onThisDayContainer = doc.selectFirst("div.mainpage-onthisday") ?: return null
        val title = onThisDayContainer.selectFirst("h2")?.text() ?: "On this day..."
        val events = onThisDayContainer.select("ul li").map { it.html() }
        return OnThisDayItem(title = title, events = events)
    }

    private fun parsePopularPages(doc: Element): List<PopularPageItem> {
        val popularContainer = doc.selectFirst("div.mainpage-popular") ?: return emptyList()
        return popularContainer.select("li a").map { link ->
            PopularPageItem(
                title = link.text(),
                pageUrl = link.attr("href").let { "$BASE_URL$it" }
            )
        }
    }
}

internal suspend fun <T> osrsFetchWithRetry(
    maxAttempts: Int,
    retryDelayMillis: Long,
    fetch: suspend (attempt: Int) -> T
): T {
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    var lastFailure: IOException? = null
    repeat(maxAttempts) { index ->
        val attempt = index + 1
        try {
            return fetch(attempt)
        } catch (failure: IOException) {
            lastFailure = failure
            if (attempt < maxAttempts && retryDelayMillis > 0) {
                delay(retryDelayMillis)
            }
        }
    }
    throw requireNotNull(lastFailure)
}
