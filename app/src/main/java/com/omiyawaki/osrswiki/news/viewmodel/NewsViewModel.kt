package com.omiyawaki.osrswiki.news.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.news.model.WikiFeed
import com.omiyawaki.osrswiki.news.repository.NewsFeedRepository
import com.omiyawaki.osrswiki.news.repository.NewsRepository
import com.omiyawaki.osrswiki.news.ui.FeedItem
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class NewsViewModel(
    application: Application,
    private val newsRepository: NewsFeedRepository = NewsRepository,
    private val networkStatus: StateFlow<Boolean> = (application as? OSRSWikiApp)?.currentNetworkStatus
        ?: MutableStateFlow(true)
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        newsRepository = NewsRepository,
        networkStatus = (application as? OSRSWikiApp)?.currentNetworkStatus ?: MutableStateFlow(true)
    )

    private val _feedItems = MutableLiveData<List<FeedItem>>()
    val feedItems: LiveData<List<FeedItem>> = _feedItems

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private val _lastUpdatedText = MutableLiveData<String>()
    val lastUpdatedText: LiveData<String> = _lastUpdatedText

    private var hasPendingConnectivityRetry = false
    private var pendingRetryForceRefresh = false
    private var lastKnownOnline = networkStatus.value

    init {
        // Initialize repository with application context
        newsRepository.initialize(application)
        
        // Load cached data synchronously for instant display
        loadCachedDataSynchronously()
        
        // Update "last updated" text
        updateLastUpdatedText()

        observeConnectivityForPendingRetry()
    }
    
    private fun loadCachedDataSynchronously() {
        newsRepository.getCachedFeedSynchronously()?.let { cachedFeed ->
            _feedItems.value = transformFeedToAdapterItems(cachedFeed)
            L.d("📦 NewsViewModel: Initialized with cached data")
        }
    }
    
    private fun updateLastUpdatedText() {
        _lastUpdatedText.value = "Last updated: ${newsRepository.getLastUpdatedString()}"
    }

    fun fetchNews(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            refreshNews(isUserInitiated = true)
            return
        }
        
        // Check if we have valid cached data and don't need to load
        if (newsRepository.isCacheValid && _feedItems.value != null) {
            L.d("📦 NewsViewModel: Using cached data")
            updateLastUpdatedText()
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = newsRepository.getWikiFeed(forceRefresh = false)
            result.onSuccess { feed ->
                clearPendingConnectivityRetry()
                preloadUpdateImages(feed)
                _feedItems.value = transformFeedToAdapterItems(feed)
                _error.value = null
                updateLastUpdatedText()
            }.onFailure { exception ->
                _error.value = NewsErrorMessageFormatter.loadMessage(exception)
                markPendingConnectivityRetry(forceRefresh = false)
            }
            _isLoading.value = false
        }
    }
    
    fun refreshNews() {
        refreshNews(isUserInitiated = true)
    }

    private fun refreshNews(isUserInitiated: Boolean) {
        viewModelScope.launch {
            L.d("🔄 NewsViewModel: Starting refresh...")
            
            if (isUserInitiated) {
                // Mark refresh attempt immediately for UX feedback
                newsRepository.markRefreshAttempt()
                updateLastUpdatedText()

                // Add haptic feedback to confirm refresh started (medium impact)
                performHapticFeedback(HapticFeedbackType.MEDIUM_IMPACT)
            }
            
            _isRefreshing.value = true
            _error.value = null
            
            val result = newsRepository.getWikiFeed(forceRefresh = true)
            result.onSuccess { feed ->
                clearPendingConnectivityRetry()
                preloadUpdateImages(feed)
                _feedItems.value = transformFeedToAdapterItems(feed)
                _error.value = null
                updateLastUpdatedText()
                
                if (isUserInitiated) {
                    // Success haptic feedback
                    performHapticFeedback(HapticFeedbackType.SUCCESS)
                }
                L.d("✅ NewsViewModel: Refresh completed successfully")
            }.onFailure { exception ->
                _error.value = NewsErrorMessageFormatter.refreshMessage(exception)
                markPendingConnectivityRetry(forceRefresh = true)
                
                if (isUserInitiated) {
                    // Error haptic feedback
                    performHapticFeedback(HapticFeedbackType.ERROR)
                }
                L.e("❌ NewsViewModel: Refresh failed: ${exception.message}")
            }
            
            _isRefreshing.value = false
        }
    }

    private fun observeConnectivityForPendingRetry() {
        viewModelScope.launch {
            networkStatus.collect { isOnline ->
                val connectivityRestored = isOnline && !lastKnownOnline
                lastKnownOnline = isOnline

                if (connectivityRestored && hasPendingConnectivityRetry) {
                    val forceRefresh = pendingRetryForceRefresh
                    clearPendingConnectivityRetry()
                    if (forceRefresh) {
                        refreshNews(isUserInitiated = false)
                    } else {
                        fetchNews(forceRefresh = false)
                    }
                }
            }
        }
    }

    private fun markPendingConnectivityRetry(forceRefresh: Boolean) {
        hasPendingConnectivityRetry = true
        pendingRetryForceRefresh = forceRefresh
    }

    private fun clearPendingConnectivityRetry() {
        hasPendingConnectivityRetry = false
        pendingRetryForceRefresh = false
    }
    
    @SuppressLint("MissingPermission")
    private fun performHapticFeedback(type: HapticFeedbackType) {
        try {
            val context = getApplication<Application>()
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    when (type) {
                        HapticFeedbackType.MEDIUM_IMPACT -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                        HapticFeedbackType.SUCCESS -> VibrationEffect.createOneShot(25, 100)
                        HapticFeedbackType.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1)
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(
                    when (type) {
                        HapticFeedbackType.MEDIUM_IMPACT -> 50
                        HapticFeedbackType.SUCCESS -> 25
                        HapticFeedbackType.ERROR -> 100
                    }
                )
            }
        } catch (e: Exception) {
            L.e("Failed to perform haptic feedback", e)
        }
    }
    
    enum class HapticFeedbackType {
        MEDIUM_IMPACT,
        SUCCESS,
        ERROR
    }

    private suspend fun preloadUpdateImages(feed: WikiFeed) {
        val imageUrls = feed.recentUpdates.mapNotNull { it.imageUrl.takeIf { url -> url.isNotEmpty() } }
        if (imageUrls.isEmpty()) {
            return
        }
        try {
            val context = getApplication<Application>().applicationContext
            val displayMetrics = context.resources.displayMetrics
            val widthDp = 280f
            val heightDp = 140f

            val rawPxWidth = widthDp * displayMetrics.density
            val rawPxHeight = heightDp * displayMetrics.density
            val widthPx = rawPxWidth.roundToInt()
            val heightPx = rawPxHeight.roundToInt()

            val preloadJobs = imageUrls.map { url ->
                viewModelScope.async(Dispatchers.IO) {
                    val bitmap = Glide.with(context)
                        .asBitmap()
                        .load(url)
                        .submit(widthPx, heightPx)
                        .get()
                    (context as OSRSWikiApp).imageCache.put(url, bitmap)
                }
            }
            preloadJobs.awaitAll()
        } catch (e: Exception) {
            L.e("Error during image preloading", e)
        }
    }

    private fun transformFeedToAdapterItems(feed: WikiFeed): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
        if (feed.recentUpdates.isNotEmpty()) {
            items.add(FeedItem.Updates(feed.recentUpdates))
        }
        if (feed.announcements.isNotEmpty()) {
            items.add(FeedItem.Announcement(feed.announcements.first()))
        }
        feed.onThisDay?.let {
            if (it.events.isNotEmpty()) {
                items.add(FeedItem.OnThisDay(it))
            }
        }
        if (feed.popularPages.isNotEmpty()) {
            items.add(FeedItem.Popular(feed.popularPages))
        }
        return items
    }
}
