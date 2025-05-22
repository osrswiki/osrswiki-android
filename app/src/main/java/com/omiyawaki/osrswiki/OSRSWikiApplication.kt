package com.omiyawaki.osrswiki

import android.app.Application
import com.omiyawaki.osrswiki.data.SearchRepository // Import SearchRepository
import com.omiyawaki.osrswiki.network.RetrofitClient // Import RetrofitClient
import com.omiyawaki.osrswiki.network.WikiApiService // Import WikiApiService

class OSRSWikiApplication : Application() {

    // Lazily initialize WikiApiService using the existing RetrofitClient.
    // RetrofitClient.apiService already provides this as a lazy singleton.
    val wikiApiService: WikiApiService by lazy {
        RetrofitClient.apiService
    }

    // Lazily initialize SearchRepository with the WikiApiService instance.
    val searchRepository: SearchRepository by lazy {
        SearchRepository(wikiApiService)
    }

    override fun onCreate() {
        super.onCreate()
        // You could perform eager initializations here if necessary,
        // but lazy initialization for services is often preferred.
        // Example: wikiApiService and searchRepository will be created when first accessed.
    }
}
