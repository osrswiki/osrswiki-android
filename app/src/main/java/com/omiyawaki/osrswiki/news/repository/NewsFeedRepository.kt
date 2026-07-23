package com.omiyawaki.osrswiki.news.repository

import android.content.Context
import com.omiyawaki.osrswiki.news.model.WikiFeed

interface NewsFeedRepository {
    fun initialize(context: Context)

    fun getCachedFeedSynchronously(): WikiFeed?

    val isCacheValid: Boolean

    fun getLastUpdatedString(): String

    fun markRefreshAttempt()

    suspend fun getWikiFeed(forceRefresh: Boolean = false): Result<WikiFeed>
}
