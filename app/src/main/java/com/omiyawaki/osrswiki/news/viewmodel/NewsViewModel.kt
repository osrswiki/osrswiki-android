package com.omiyawaki.osrswiki.news.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.news.model.WikiFeed
import com.omiyawaki.osrswiki.news.repository.NewsRepository
import com.omiyawaki.osrswiki.news.ui.FeedItem
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val _feedItems = MutableLiveData<List<FeedItem>>()
    val feedItems: LiveData<List<FeedItem>> = _feedItems

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun fetchNews() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = NewsRepository.getWikiFeed()
            result.onSuccess { feed ->
                preloadUpdateImages(feed)
                _feedItems.value = transformFeedToAdapterItems(feed)
                _error.value = null
            }.onFailure { exception ->
                _error.value = "Failed to load feed: ${exception.message}"
            }
            _isLoading.value = false
        }
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