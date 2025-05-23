package com.omiyawaki.osrswiki.ui.article

import android.app.Application // Added import for Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import com.omiyawaki.osrswiki.OSRSWikiApplication // Added import for OSRSWikiApplication
import com.omiyawaki.osrswiki.data.ArticleRepository
// RetrofitClient import is no longer directly needed here if apiService comes from OSRSWikiApplication
import com.omiyawaki.osrswiki.network.WikiApiService // Still needed for type
import com.omiyawaki.osrswiki.data.db.dao.ArticleDao // Added for type, good practice
import com.omiyawaki.osrswiki.data.db.dao.ArticleFtsDao // Added for type, good practice

/**
 * Factory for creating [ArticleViewModel] instances.
 *
 * This factory implements [ViewModelProvider.Factory]. It is responsible for
 * instantiating the [ArticleViewModel] with its necessary dependencies, including
 * the [Application] context (for accessing services like the [ArticleRepository])
 * and a [SavedStateHandle] (obtained via [CreationExtras]).
 *
 * @param application The application instance, which must be an [OSRSWikiApplication],
 * used to retrieve global dependencies.
 */
class ArticleViewModelFactory(
    private val application: Application // Added application parameter
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>, // The class of the ViewModel to be created.
        extras: CreationExtras // Used to access SavedStateHandle etc.
    ): T {
        val handle = extras.createSavedStateHandle()
        // Check if the requested ViewModel class is ArticleViewModel or a subclass.
        if (modelClass.isAssignableFrom(ArticleViewModel::class.java)) {
            // Ensure the application context is OSRSWikiApplication
            val osrsApplication = application as? OSRSWikiApplication
                ?: throw IllegalStateException(
                    "Application context must be an instance of OSRSWikiApplication. " +
                    "Found: \${application.javaClass.name}"
                )

            // 1. Obtain dependencies from OSRSWikiApplication
            val apiService: WikiApiService = osrsApplication.wikiApiService
            val articleDao: ArticleDao = osrsApplication.articleDao
            val articleFtsDao: ArticleFtsDao = osrsApplication.articleFtsDao

            // 2. Create the ArticleRepository instance, injecting all dependencies.
            val repository = ArticleRepository(apiService, articleDao, articleFtsDao)

            // 3. Create and return the ArticleViewModel instance, providing its dependencies.
            @Suppress("UNCHECKED_CAST")
            return ArticleViewModel(repository, handle) as T
        }
        // If the requested ViewModel class is not ArticleViewModel, throw an exception,
        // as this factory is specialized for ArticleViewModel.
        throw IllegalArgumentException(
            "Unknown ViewModel class: \${modelClass.name}. " +
            "This factory is designed to create instances of ArticleViewModel only."
        )
    }
}
