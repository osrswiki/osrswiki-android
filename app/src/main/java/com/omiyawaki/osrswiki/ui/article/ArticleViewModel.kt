package com.omiyawaki.osrswiki.ui.article

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omiyawaki.osrswiki.data.ArticleRepository
import com.omiyawaki.osrswiki.data.Result // Ensure this is the correct Result type from ArticleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

// Data class ArticleUiState
data class ArticleUiState(
    val isLoading: Boolean = true,
    val title: String? = null,
    val htmlContent: String? = null,
    val imageUrl: String? = null,
    val error: String? = null
)

class ArticleViewModel(
    private val articleRepository: ArticleRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        private const val TAG = "ArticleViewModel"
        const val NAV_ARG_ARTICLE_ID = "articleId"
        const val NAV_ARG_ARTICLE_TITLE = "articleTitle"
    }

    private val _uiState = MutableStateFlow(ArticleUiState(isLoading = true))
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "ArticleViewModel initialized.")
        fetchArticleData()
    }

    private fun fetchArticleData() {
        val articleIdArg: String? = savedStateHandle.get(NAV_ARG_ARTICLE_ID)
        val articleTitleArg: String? = savedStateHandle.get(NAV_ARG_ARTICLE_TITLE)

        Log.d(TAG, "Attempting to load article. NavArg ID: '$articleIdArg', NavArg Title: '$articleTitleArg'")
        _uiState.value = ArticleUiState(isLoading = true)

        viewModelScope.launch {
            var fetchedApiTitle: String? = null
            var fetchedHtmlContent: String? = null
            var fetchedImageUrl: String? = null
            var fetchError: String? = null
            var usedIdentifierForLog = "N/A"

            try {
                // Attempt to use articleId first if it's valid
                if (articleIdArg != null && articleIdArg != "null" && articleIdArg.isNotEmpty()) {
                    try {
                        val pageId = articleIdArg.toInt()
                        usedIdentifierForLog = "pageId $pageId"
                        Log.d(TAG, "Fetching article data by pageId: $pageId using articleRepository.getArticle")

                        val result = articleRepository.getArticle(pageId, forceNetwork = false)
                            .firstOrNull { it is Result.Success || it is Result.Error }

                        when (result) {
                            is Result.Success -> {
                                val articleData = result.data
                                fetchedApiTitle = articleData.title
                                fetchedHtmlContent = articleData.htmlContent
                                fetchedImageUrl = articleData.mainImageUrl
                                Log.i(TAG, "Successfully fetched data by pageId: $pageId. API Title: '$fetchedApiTitle'")
                            }
                            is Result.Error -> {
                                fetchError = result.message
                                Log.w(TAG, "Failed to load article content by pageId: $pageId. Repository returned error: ${result.message}")
                            }
                            is Result.Loading -> {
                                Log.d(TAG, "Article fetching for pageId $pageId is Loading.")
                            }
                            null -> {
                                fetchError = "Failed to load article content for ID: $pageId. No data received from repository."
                                Log.w(TAG, "No Success/Error result from getArticle for pageId: $pageId.")
                            }
                        }
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Failed to parse article ID '$articleIdArg' to Int. Will try fetching by title if available.", e)
                        fetchError = "Invalid Article ID format: '$articleIdArg'."
                    }
                }

                // If fetching by ID was not attempted or failed, AND a valid articleTitleArg is present, then attempt to fetch by title.
                if ((articleIdArg.isNullOrEmpty() || articleIdArg == "null" || fetchError != null || fetchedHtmlContent == null) &&
                    !articleTitleArg.isNullOrEmpty() && articleTitleArg != "null"
                ) {
                    val idFetchAttempted = articleIdArg != null && articleIdArg != "null" && articleIdArg.isNotEmpty()
                    if (fetchError != null && idFetchAttempted) {
                        Log.d(TAG, "Previous attempt with ID '$articleIdArg' resulted in error/no content ('$fetchError'). Now attempting fetch by title: '$articleTitleArg'")
                        // Keep existing fetchError to potentially combine messages if title fetch also fails or for context
                    } else if (!idFetchAttempted) {
                         Log.d(TAG, "No valid ID provided or attempted. Attempting fetch by title: '$articleTitleArg'")
                    }


                    usedIdentifierForLog = "title '$articleTitleArg'"
                    Log.d(TAG, "Fetching article data by title: '$articleTitleArg' using articleRepository.getArticleByTitle")

                    val resultByTitle = articleRepository.getArticleByTitle(articleTitleArg, forceNetwork = true) // Assuming forceNetwork for now
                        .firstOrNull { it is Result.Success || it is Result.Error }

                    when (resultByTitle) {
                        is Result.Success -> {
                            val articleData = resultByTitle.data
                            fetchedApiTitle = articleData.title
                            fetchedHtmlContent = articleData.htmlContent
                            fetchedImageUrl = articleData.mainImageUrl
                            Log.i(TAG, "Successfully fetched data by title: '$articleTitleArg'. API Title: '$fetchedApiTitle'")
                            fetchError = null // Successfully fetched by title, clear any prior error from ID attempt.
                        }
                        is Result.Error -> {
                            val titleErrorMessage = "Failed to load article by title '$articleTitleArg': ${resultByTitle.message}"
                            fetchError = if (fetchError != null && idFetchAttempted) "$fetchError. $titleErrorMessage" else titleErrorMessage
                            Log.w(TAG, titleErrorMessage)
                        }
                        is Result.Loading -> {
                             Log.d(TAG, "Article fetching for title '$articleTitleArg' is Loading.")
                        }
                        null -> {
                            val titleErrorMessage = "Failed to load article by title '$articleTitleArg'. No data received from repository."
                            fetchError = if (fetchError != null && idFetchAttempted) "$fetchError. $titleErrorMessage" else titleErrorMessage
                            Log.w(TAG, titleErrorMessage)
                        }
                    }
                     // If API title is null after attempting fetch by title (e.g. error case), use the input title for error display.
                    if (fetchedApiTitle == null && fetchError != null) fetchedApiTitle = articleTitleArg

                } else if (fetchError == null && fetchedHtmlContent == null) {
                     // This condition means neither ID nor Title path yielded content, and no explicit error set prior.
                     // This typically means no valid identifier was provided to begin with.
                    if ((articleIdArg.isNullOrEmpty() || articleIdArg == "null") && (articleTitleArg.isNullOrEmpty() || articleTitleArg == "null")) {
                         fetchError = "Article identifier (ID or Title) not provided or invalid."
                         Log.e(TAG, fetchError)
                    } else {
                        // This case should ideally be covered by specific error setting within ID/Title blocks
                        // If fetchError is null here but content is null, it's an unexpected state.
                        Log.w(TAG, "Reached end of fetch logic with no content and no error; identifier: $usedIdentifierForLog")
                        fetchError = "Failed to load article using identifier: $usedIdentifierForLog. Reason unclear."
                    }
                }

                // Update UI State
                if (fetchError == null && fetchedHtmlContent != null) {
                    _uiState.value = ArticleUiState(
                        isLoading = false,
                        title = fetchedApiTitle,
                        htmlContent = fetchedHtmlContent,
                        imageUrl = fetchedImageUrl,
                        error = null
                    )
                } else {
                    _uiState.value = ArticleUiState(
                        isLoading = false,
                        title = fetchedApiTitle ?: articleTitleArg ?: if (articleIdArg != "null" && !articleIdArg.isNullOrEmpty()) articleIdArg else null,
                        htmlContent = null,
                        imageUrl = null,
                        error = fetchError ?: "An unexpected error occurred while loading the article: $usedIdentifierForLog."
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception while fetching article data using identifier: $usedIdentifierForLog", e)
                _uiState.value = ArticleUiState(
                    isLoading = false,
                    title = articleTitleArg ?: if (articleIdArg != "null" && !articleIdArg.isNullOrEmpty()) articleIdArg else null,
                    error = "An error occurred: ${e.localizedMessage}"
                )
            }
        }
    }

@Suppress("unused") // For upcoming pull-to-refresh feature
    fun refreshArticle() {
        Log.d(TAG, "refreshArticle() called. Re-fetching data.")
        fetchArticleData()
    }
}
