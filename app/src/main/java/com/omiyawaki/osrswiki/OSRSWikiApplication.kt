package com.omiyawaki.osrswiki

import android.app.Application
import android.util.Log
import com.omiyawaki.osrswiki.data.db.OSRSWikiDatabase         // Your Room database class
import com.omiyawaki.osrswiki.data.db.dao.ArticleMetaDao    // Your DAO
import com.omiyawaki.osrswiki.data.repository.ArticleRepository
import com.omiyawaki.osrswiki.network.RetrofitClient          // Your Retrofit client object
import com.omiyawaki.osrswiki.network.WikiApiService        // Your Retrofit service interface

class OSRSWikiApplication : Application() {

    // --- Manually managed singleton dependencies ---

    private val database: OSRSWikiDatabase by lazy {
        OSRSWikiDatabase.getInstance(applicationContext) // Using the getInstance method from your DB class
    }

    private val articleMetaDao: ArticleMetaDao by lazy {
        database.articleMetaDao() // Accessing the DAO from your DB instance
    }

    private val wikiApiService: WikiApiService by lazy {
        RetrofitClient.apiService // Accessing the service from your RetrofitClient object
    }

    // ArticleRepository is publicly accessible for PageFragment
    lateinit var articleRepository: ArticleRepository
        private set // Make setter private to control instantiation from within Application class

    // You can define other repositories here if needed, e.g.:
    // lateinit var searchRepository: SearchRepository
    //     private set

    // --- Application Lifecycle ---

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "OSRSWikiApplication created and manual DI initializing...")

        // Initialize repositories that depend on other services/DAOs
        articleRepository = ArticleRepository(
            mediaWikiApiService = wikiApiService, // from lazy delegate
            articleMetaDao = articleMetaDao,     // from lazy delegate
            applicationContext = this            // provide application context
        )

        // Example for another repository:
        // searchRepository = SearchRepository(wikiApiService, database.searchResultDao(), ...)

        Log.d(TAG, "Manual DI setup complete in OSRSWikiApplication.")
    }

    companion object {
        private const val TAG = "OSRSWikiApplication"
        lateinit var instance: OSRSWikiApplication
            private set

        // Example: Manual crash logging function (if you still need it)
        fun logCrashManually(throwable: Throwable) {
            Log.e("OSRSWikiAppCrash", "Manual crash log from Application", throwable)
            // Consider adding more sophisticated logging here if needed
        }
    }
}
