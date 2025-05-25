package com.omiyawaki.osrswiki

import android.app.Application
import android.util.Log // Added for logCrashManually
import com.omiyawaki.osrswiki.data.repository.ArticleRepository // This is for the ArticleRepository in data/repository
import com.omiyawaki.osrswiki.data.SearchRepository // Changed to use the SearchRepository in data package
import com.omiyawaki.osrswiki.data.db.OSRSWikiDatabase
import com.omiyawaki.osrswiki.data.db.dao.ArticleMetaDao
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.network.WikiApiService

class OSRSWikiApplication : Application() {

    companion object {
        lateinit var instance: OSRSWikiApplication
            private set // Restrict setting the instance from outside the class
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Other application initialization code can go here, if any.
    }

    // Lazily initialize WikiApiService using the existing RetrofitClient.
    @Suppress("unused")
    private val wikiApiService: WikiApiService by lazy {
        RetrofitClient.apiService
    }

    // Lazily initialize the Room database.
    @Suppress("unused")
    private val database: OSRSWikiDatabase by lazy {
        OSRSWikiDatabase.getInstance(applicationContext)
    }

    // Lazily initialize ArticleMetaDao from the database.
    @Suppress("unused")
    private val articleMetaDao: ArticleMetaDao by lazy {
        database.articleMetaDao()
    }

    // Provides the ArticleRepository from the data.repository package.
    @Suppress("unused")
    val articleRepository: ArticleRepository by lazy {
        ArticleRepository(wikiApiService, articleMetaDao, applicationContext)
    }


    // Updated SearchRepository instantiation to use com.omiyawaki.osrswiki.data.SearchRepository
    // and provide its required dependencies: wikiApiService, articleMetaDao.
    @Suppress("unused")
    val searchRepository: SearchRepository by lazy {
        SearchRepository(wikiApiService, this.articleMetaDao)
    }

    /**
     * Logs a throwable manually, intended for reporting to a crash analysis service.
     * Called by L.kt when a remote error is detected in a production release.
     *
     * @param t The throwable to log.
     */
    fun logCrashManually(t: Throwable) {
        // This is where a remote crash reporting tool (e.g., Firebase Crashlytics)
        // would typically be invoked to record the error.
        // For now, as a placeholder, we'll log it to Logcat with a distinct tag.
        // TODO: Replace this with actual crash reporting (e.g., FirebaseCrashlytics.recordException(t))
        Log.e("OSRSWikiAppCrash", "Manual crash report: ${t.message}", t)
    }
}
