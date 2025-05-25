package com.omiyawaki.osrswiki.ui.article

import android.app.Application // Added import for Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import com.omiyawaki.osrswiki.OSRSWikiApplication // Added import for OSRSWikiApplication
import com.omiyawaki.osrswiki.data.repository.ArticleRepository
// RetrofitClient import is no longer directly needed here if apiService comes from OSRSWikiApplication

/**
 * Factory for creating [ArticleViewModel] instances.
 *
 * This factory implements [ViewModelProvider.Factory]. It is responsible for
 * instantiating the [ArticleViewModel] with its necessary dependencies, including
 * the [Application] context (for accessing services like the [ArticleRepository]),
 * a [SavedStateHandle] (obtained via [CreationExtras]), and the article identifiers.
 *
 * @param application The application instance, which must be an [OSRSWikiApplication],
 * used to retrieve global dependencies.
 * @param articleId The ID of the article to be loaded, can be null.
 * @param articleTitle The title of the article, can be null.
 */
@Suppress("unused")
class ArticleViewModelFactory(
    private val application: Application,
    private val articleId: String?,
    private val articleTitle: String?
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
            val repository = osrsApplication.articleRepository

            // 3. Create and return the ArticleViewModel instance, providing its dependencies.
            @Suppress("UNCHECKED_CAST")
            return ArticleViewModel(repository, handle, articleId, articleTitle) as T // Pass articleId and articleTitle
        }
        // If the requested ViewModel class is not ArticleViewModel, throw an exception,
        // as this factory is specialized for ArticleViewModel.
        throw IllegalArgumentException(
            "Unknown ViewModel class: \${modelClass.name}. " +
            "This factory is designed to create instances of ArticleViewModel only."
        )
    }
}
