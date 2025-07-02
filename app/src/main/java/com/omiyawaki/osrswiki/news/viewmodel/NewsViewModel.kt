package com.omiyawaki.osrswiki.news.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omiyawaki.osrswiki.news.model.WikiFeed
import com.omiyawaki.osrswiki.news.repository.NewsRepository
import com.omiyawaki.osrswiki.news.ui.FeedItem
import kotlinx.coroutines.launch

/**
 * ViewModel for the NewsFragment.
 * This class is responsible for fetching, preparing, and exposing the news feed
 * data for display in the UI.
 */
class NewsViewModel : ViewModel() {

    private val _feedItems = MutableLiveData<List<FeedItem>>()
    val feedItems: LiveData<List<FeedItem>> = _feedItems

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Fetches the news feed from the repository and transforms it into a list
     * of FeedItem objects suitable for the adapter.
     */
    fun fetchNews() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = NewsRepository.getWikiFeed()
            result.onSuccess { feed ->
                _feedItems.value = transformFeedToAdapterItems(feed)
                _error.value = null
            }.onFailure { exception ->
                _error.value = "Failed to load feed: ${exception.message}"
            }
            _isLoading.value = false
        }
    }

    private fun transformFeedToAdapterItems(feed: WikiFeed): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
        // Add sections to the list only if they have content.
        if (feed.recentUpdates.isNotEmpty()) {
            items.add(FeedItem.Updates(feed.recentUpdates))
        }
        if (feed.announcements.isNotEmpty()) {
            // For simplicity, we'll just show the first announcement.
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
