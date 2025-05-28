package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.R // Required for R.string.label_loading
import com.omiyawaki.osrswiki.util.Result
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Handles the logic of fetching article data using PageRepository
 * and updating the PageViewModel. Signals updates via a callback.
 */
class PageContentLoader(
    private val pageRepository: PageRepository,
    private val pageViewModel: PageViewModel,
    private val coroutineScope: CoroutineScope, // e.g., lifecycleScope from Fragment
    private val onStateUpdated: () -> Unit     // Callback to notify when UI should refresh
) {
    fun loadPageByTitle(articleQueryTitle: String, forceNetwork: Boolean = false) {
        // Update ViewModel to loading state
        pageViewModel.uiState = PageUiState(isLoading = true, title = articleQueryTitle, pageId = null)
        L.d("PageContentLoader: Set state to loading for title '$articleQueryTitle'")
        onStateUpdated() // Notify UI for initial loading state

        L.d("PageContentLoader: Loading article by title '$articleQueryTitle', forceNetwork: $forceNetwork")

        coroutineScope.launch {
            pageRepository.getArticleByTitle(title = articleQueryTitle, forceNetwork = forceNetwork)
                .catch { e -> // Catch exceptions from the flow itself
                    L.e("PageContentLoader: Flow collection error for title '$articleQueryTitle'", e)
                    pageViewModel.uiState = PageUiState(
                        isLoading = false,
                        title = articleQueryTitle, // Keep the queried title for context
                        pageId = null, // ID is unknown/irrelevant if load by title failed this way
                        error = "Failed to load article due to an unexpected error: ${e.message}"
                    )
                    onStateUpdated() // Notify UI
                }
                .collectLatest { result ->
                    L.d("PageContentLoader: Received result for title '$articleQueryTitle': $result")
                    when (result) {
                        is Result.Loading -> {
                            pageViewModel.uiState = PageUiState(isLoading = true, title = articleQueryTitle, pageId = null)
                        }
                        is Result.Success -> {
                            pageViewModel.uiState = result.data // result.data is PageUiState
                            L.i("PageContentLoader: Successfully loaded article: '${result.data.title}' (queried as title '$articleQueryTitle') with pageId ${result.data.pageId}")
                        }
                        is Result.Error -> {
                            L.e("PageContentLoader: Error loading article with title '$articleQueryTitle': ${result.message}", result.throwable)
                            pageViewModel.uiState = PageUiState(
                                isLoading = false,
                                title = articleQueryTitle, // Keep the queried title for context
                                pageId = null,
                                error = "Error: ${result.message}"
                            )
                        }
                    }
                    onStateUpdated() // Notify UI after processing the result
                }
        }
    }

    // Added initialDisplayTitle parameter
    fun loadPageById(pageId: Int, initialDisplayTitle: String?, forceNetwork: Boolean = false) {
        val displayTitleDuringLoad = initialDisplayTitle ?: "Loading..." // Or use a string resource like R.string.label_loading

        // Update ViewModel to loading state
        pageViewModel.uiState = PageUiState(isLoading = true, title = displayTitleDuringLoad, pageId = pageId)
        L.d("PageContentLoader: Set state to loading for page ID '$pageId', display title: '$displayTitleDuringLoad'")
        onStateUpdated() // Notify UI for initial loading state

        L.d("PageContentLoader: Loading article by page ID '$pageId', forceNetwork: $forceNetwork")

        coroutineScope.launch {
            pageRepository.getArticle(pageId = pageId, forceNetwork = forceNetwork)
                .catch { e -> // Catch exceptions from the flow itself
                    L.e("PageContentLoader: Flow collection error for page ID '$pageId'", e)
                    pageViewModel.uiState = PageUiState(
                        isLoading = false,
                        title = displayTitleDuringLoad, // Use initial display title for context on error
                        pageId = pageId,
                        error = "Failed to load article (ID: $pageId) due to an unexpected error: ${e.message}"
                    )
                    onStateUpdated() // Notify UI
                }
                .collectLatest { result ->
                    L.d("PageContentLoader: Received result for page ID '$pageId': $result")
                    when (result) {
                        is Result.Loading -> {
                             pageViewModel.uiState = PageUiState(isLoading = true, title = displayTitleDuringLoad, pageId = pageId)
                        }
                        is Result.Success -> {
                            pageViewModel.uiState = result.data // result.data is PageUiState which has the canonical title and pageId
                            L.i("PageContentLoader: Successfully loaded article: '${result.data.title}' (queried by ID '$pageId', received pageId ${result.data.pageId})")
                        }
                        is Result.Error -> {
                            L.e("PageContentLoader: Error loading article with page ID '$pageId': ${result.message}", result.throwable)
                            pageViewModel.uiState = PageUiState(
                                isLoading = false,
                                title = displayTitleDuringLoad, // Use initial display title for context on error
                                pageId = pageId,
                                error = "Error loading page (ID: $pageId): ${result.message}"
                            )
                        }
                    }
                    onStateUpdated() // Notify UI after processing the result
                }
        }
    }
}
