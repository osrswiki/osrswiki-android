package com.omiyawaki.osrswiki

import android.app.Application
import com.omiyawaki.osrswiki.data.repository.ArticleRepository // This is for the ArticleRepository in data/repository
import com.omiyawaki.osrswiki.data.SearchRepository // Changed to use the SearchRepository in data package
import com.omiyawaki.osrswiki.data.db.OSRSWikiDatabase
import com.omiyawaki.osrswiki.data.db.dao.ArticleMetaDao
import com.omiyawaki.osrswiki.data.db.dao.ArticleDao     // Ensured ArticleDao import is present
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.network.WikiApiService

class OSRSWikiApplication : Application() {

    // Lazily initialize WikiApiService using the existing RetrofitClient.
    private val wikiApiService: WikiApiService by lazy {
        RetrofitClient.apiService
    }

    // Lazily initialize the Room database.
    private val database: OSRSWikiDatabase by lazy {
        OSRSWikiDatabase.getInstance(applicationContext)
    }

    // Lazily initialize ArticleMetaDao from the database.
    val articleMetaDao: ArticleMetaDao by lazy {
        database.articleMetaDao()
    }

    // ArticleDao is needed by the updated SearchRepository (com.omiyawaki.osrswiki.data.SearchRepository)
    private val articleDao: ArticleDao by lazy {
        database.articleDao()
    }

    // Provides the ArticleRepository from the data.repository package.
    val articleRepository: ArticleRepository by lazy {
        ArticleRepository(wikiApiService, articleMetaDao, applicationContext)
    }

    // Updated SearchRepository instantiation to use com.omiyawaki.osrswiki.data.SearchRepository
    // and provide its required dependencies: wikiApiService, articleDao, articleMetaDao.
    val searchRepository: SearchRepository by lazy {
        SearchRepository(wikiApiService, this.articleDao, this.articleMetaDao)
    }

}