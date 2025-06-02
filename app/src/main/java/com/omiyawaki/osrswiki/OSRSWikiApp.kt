package com.omiyawaki.osrswiki

import android.app.Application
import android.util.Log
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.page.PageRepository
import com.omiyawaki.osrswiki.search.SearchRepository
import com.omiyawaki.osrswiki.util.NetworkMonitor // <<< ADDED Import for NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow // <<< ADDED Import for StateFlow

class OSRSWikiApp : Application() {
    // Define an application-wide CoroutineScope
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- Singleton dependencies provided by the Application class ---

    // Database instance (AppDatabase.instance itself is a lazy singleton)
    private val database: AppDatabase by lazy {
        AppDatabase.instance // This uses OSRSWikiApp.instance.applicationContext internally
    }

    // DAO instances
    private val articleMetaDao: ArticleMetaDao by lazy {
        database.articleMetaDao()
    }

    private val offlinePageFtsDao: OfflinePageFtsDao by lazy {
        database.offlinePageFtsDao()
    }

    // Network service instance
    private val wikiApiService: WikiApiService by lazy {
        RetrofitClient.apiService
    }

    // Network Monitor instance
    private val networkMonitor: NetworkMonitor by lazy { // <<< ADDED NetworkMonitor instantiation
        NetworkMonitor(applicationContext) // `this` or `applicationContext` can be used here
    }

    // Publicly accessible StateFlow for network status
    val currentNetworkStatus: StateFlow<Boolean> by lazy { // <<< ADDED Public accessor for isOnline StateFlow
        networkMonitor.isOnline // Exposes the StateFlow from NetworkMonitor
    }

    // Repository instances
    val pageRepository: PageRepository by lazy {
        PageRepository(
            mediaWikiApiService = wikiApiService,
            articleMetaDao = articleMetaDao,
            applicationContext = this
        )
    }

    val searchRepository: SearchRepository by lazy {
        SearchRepository(
            apiService = wikiApiService,
            articleMetaDao = articleMetaDao,
            offlinePageFtsDao = offlinePageFtsDao
        )
    }

    // --- Application Lifecycle ---

    override fun onCreate() {
        super.onCreate()
        instance = this // Set the static instance reference
        Log.d(TAG, "OSRSWikiApplication created and instance set.")
        // networkMonitor and other lazy properties will be initialized on first access.
    }

    companion object {
        private const val TAG = "OSRSWikiApplication"
        lateinit var instance: OSRSWikiApp
            private set // Setter remains private

        fun logCrashManually(throwable: Throwable) {
            Log.e("OSRSWikiAppCrash", "Manual crash log from Application", throwable)
        }
    }
}