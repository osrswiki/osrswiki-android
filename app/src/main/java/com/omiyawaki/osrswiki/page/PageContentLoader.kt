package com.omiyawaki.osrswiki.ui.article

import com.omiyawaki.osrswiki.data.repository.ArticleRepository
import com.omiyawaki.osrswiki.page.PageViewModel
import com.omiyawaki.osrswiki.util.Result
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Handles the logic of fetching article data using ArticleRepository
 * and updating the PageViewModel. Signals updates via a callback.
 */
class ArticleContentLoader(
    private val articleRepository: ArticleRepository,
    private val pageViewModel: PageViewModel,
    private val coroutineScope: CoroutineScope, // e.g., lifecycleScope from Fragment
    private val onStateUpdated: () -> Unit    // Callback to notify when UI should refresh
) {
    fun loadArticleByTitle(articleQueryTitle: String, forceNetwork: Boolean = false) {
        // Update ViewModel to loading state
        pageViewModel.uiState = ArticleUiState(isLoading = true, title = articleQueryTitle)
        L.d("ArticleContentLoader: Set state to loading for '$articleQueryTitle'")
        onStateUpdated() // Notify UI for initial loading state

        L.d("ArticleContentLoader: Loading article by title '$articleQueryTitle', forceNetwork: $forceNetwork")

        coroutineScope.launch {
            articleRepository.getArticleByTitle(title = articleQueryTitle, forceNetwork = forceNetwork)
                .catch { e -> // Catch exceptions from the flow itself
                    L.e("ArticleContentLoader: Flow collection error for title '$articleQueryTitle'", e)
                    pageViewModel.uiState = ArticleUiState(
                        isLoading = false,
                        title = articleQueryTitle,
                        error = "Failed to load article due to an unexpected error: ${e.message}"
                    )
                    onStateUpdated() // Notify UI
                }
                .collectLatest { result ->
                    L.d("ArticleContentLoader: Received result for '$articleQueryTitle': $result")
                    when (result) {
                        is Result.Loading -> {
                            pageViewModel.uiState = ArticleUiState(isLoading = true, title = articleQueryTitle)
                        }
                        is Result.Success -> {
                            pageViewModel.uiState = result.data // result.data is ArticleUiState
                            L.i("ArticleContentLoader: Successfully loaded article: '${result.data.title}' (queried as '$articleQueryTitle')")
                        }
                        is Result.Error -> {
                            L.e("ArticleContentLoader: Error loading article '$articleQueryTitle': ${result.message}", result.throwable)
                            pageViewModel.uiState = ArticleUiState(
                                isLoading = false,
                                title = articleQueryTitle,
                                error = "Error: ${result.message}"
                            )
                        }
                    }
                    onStateUpdated() // Notify UI after processing the result
                }
        }
    }
}
