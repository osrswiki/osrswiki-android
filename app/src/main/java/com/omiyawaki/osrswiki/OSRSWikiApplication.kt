package com.omiyawaki.osrswiki

import android.app.Application
import android.util.Log
import com.omiyawaki.osrswiki.database.AppDatabase      // Room database class
import com.omiyawaki.osrswiki.database.ArticleMetaDao  // DAO
import com.omiyawaki.osrswiki.data.repository.ArticleRepository
import com.omiyawaki.osrswiki.data.SearchRepository         // Import SearchRepository
import com.omiyawaki.osrswiki.network.RetrofitClient       // Retrofit client object
import com.omiyawaki.osrswiki.network.WikiApiService       // Retrofit service interface

class OSRSWikiApplication : Application() {

    // --- Manually managed singleton dependencies ---

    // Updated to use the companion object instance from OSRSWikiDatabase
    private val database: AppDatabase by lazy {
        AppDatabase.instance
    }

    private val articleMetaDao: ArticleMetaDao by lazy {
        database.articleMetaDao() // Accessing the DAO from DB instance
    }

    private val wikiApiService: WikiApiService by lazy {
        RetrofitClient.apiService // Accessing the service from RetrofitClient object
    }

    // --- Publicly accessible repositories ---
    lateinit var articleRepository: ArticleRepository
        private set // Make setter private to control instantiation from within Application class

    lateinit var searchRepository: SearchRepository
        private set // Make setter private to control instantiation from within Application class

    // --- Application Lifecycle ---

    override fun onCreate() {
        super.onCreate()
        instance = this // Ensure instance is set before any dependencies might need it
        Log.d(TAG, "OSRSWikiApplication created and manual DI initializing...")

        // Initialize repositories that depend on other services/DAOs
        articleRepository = ArticleRepository(
            mediaWikiApiService = wikiApiService, // from lazy delegate
            articleMetaDao = articleMetaDao,     // from lazy delegate
            applicationContext = this            // provide application context
        )

        searchRepository = SearchRepository(
            apiService = wikiApiService,        // from lazy delegate
            articleMetaDao = articleMetaDao     // from lazy delegate
        )

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
