package com.omiyawaki.osrswiki

import android.app.Application
import com.omiyawaki.osrswiki.data.SearchRepository
import com.omiyawaki.osrswiki.data.db.OSRSWikiDatabase
import com.omiyawaki.osrswiki.data.db.dao.ArticleDao
import com.omiyawaki.osrswiki.data.db.dao.ArticleFtsDao // Added import for ArticleFtsDao
import com.omiyawaki.osrswiki.data.ArticleRepository
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.network.WikiApiService

class OSRSWikiApplication : Application() {

    // Lazily initialize WikiApiService using the existing RetrofitClient.
    val wikiApiService: WikiApiService by lazy {
        RetrofitClient.apiService
    }

    // Lazily initialize the Room database.
    // This provides a single instance of the database for the entire application.
    private val database: OSRSWikiDatabase by lazy {
        OSRSWikiDatabase.getInstance(applicationContext)
    }

    // Lazily initialize ArticleDao from the database.
    val articleDao: ArticleDao by lazy {
        database.articleDao()
    }

    // Lazily initialize ArticleFtsDao from the database.
    val articleFtsDao: ArticleFtsDao by lazy {
        database.articleFtsDao()
    }

    // Lazily initialize SearchRepository.
    // Now includes ArticleFtsDao for local FTS-based searches.
    val searchRepository: SearchRepository by lazy {
        SearchRepository(wikiApiService, articleDao, articleFtsDao) // Use the new articleFtsDao property
    }

    // Provides the ArticleRepository for fetching and caching article data.
    // Now includes ArticleFtsDao for FTS table updates.
@Suppress("unused") // Will be used for article download/local storage feature
    val articleRepository: ArticleRepository by lazy {
        ArticleRepository(wikiApiService, articleDao, articleFtsDao) // Use the new articleFtsDao property
    }


    override fun onCreate() {
        super.onCreate()
        // Lazy initializations mean services/database are created only when first accessed.
    }
}
