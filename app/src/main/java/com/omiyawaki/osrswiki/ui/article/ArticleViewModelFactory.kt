package com.omiyawaki.osrswiki.ui.article

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.omiyawaki.osrswiki.data.ArticleRepository
import com.omiyawaki.osrswiki.network.RetrofitClient // For accessing the apiService
import com.omiyawaki.osrswiki.network.WikiApiService  // Type for apiService

/**
 * Factory for creating [ArticleViewModel] instances.
 *
 * This factory extends [AbstractSavedStateViewModelFactory], which allows the
 * [SavedStateHandle] to be passed to the ViewModel. It is responsible for
 * instantiating the [ArticleViewModel] with its necessary dependencies,
 * namely the [ArticleRepository].
 *
 * @param owner The [SavedStateRegistryOwner] (typically the Fragment or Activity) which
 * provides the scope for the ViewModel and the [SavedStateHandle].
 * @param defaultArgs Optional [Bundle] of arguments to be passed to the [SavedStateHandle].
 * These are usually supplied by the Navigation component if arguments are defined in the nav graph.
 */
class ArticleViewModelFactory(
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    override fun <T : ViewModel> create(
        key: String,        // A key that identifies the ViewModel (usually the ViewModel's class name).
        modelClass: Class<T>, // The class of the ViewModel to be created.
        handle: SavedStateHandle // The SavedStateHandle for this ViewModel, provided by the system.
    ): T {
        // Check if the requested ViewModel class is ArticleViewModel or a subclass.
        if (modelClass.isAssignableFrom(ArticleViewModel::class.java)) {
            // 1. Obtain the WikiApiService instance from our RetrofitClient singleton.
            val apiService: WikiApiService = RetrofitClient.apiService

            // 2. Create the ArticleRepository instance, injecting the apiService.
            val repository = ArticleRepository(apiService)

            // 3. Create and return the ArticleViewModel instance, providing its dependencies.
            // The Suppress annotation is used because the cast to T is safe here due to
            // the isAssignableFrom check.
            @Suppress("UNCHECKED_CAST")
            return ArticleViewModel(repository, handle) as T
        }
        // If the requested ViewModel class is not ArticleViewModel, throw an exception,
        // as this factory is specialized for ArticleViewModel.
        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}. " +
            "This factory is designed to create instances of ArticleViewModel only."
        )
    }
}
