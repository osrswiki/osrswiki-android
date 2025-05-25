package com.omiyawaki.osrswiki.ui.article

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omiyawaki.osrswiki.data.repository.ArticleRepository
import com.omiyawaki.osrswiki.util.Result // Ensure this is the correct Result type from ArticleRepository
import com.omiyawaki.osrswiki.util.Event
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

// Data class ArticleUiState

// Sealed class for user messages related to offline actions
sealed class ArticleOfflineUserMessage {
    data class Success(val message: String) : ArticleOfflineUserMessage()
    data class Error(val message: String) : ArticleOfflineUserMessage()
}

@Suppress("unused")
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

    private val _isArticleOffline = MutableStateFlow(false)
    val isArticleOffline: StateFlow<Boolean> = _isArticleOffline.asStateFlow()
    private var offlineStatusJob: Job? = null

    private val _offlineActionMessage = MutableStateFlow<Event<ArticleOfflineUserMessage>?>(null)
    val offlineActionMessage: StateFlow<Event<ArticleOfflineUserMessage>?> = _offlineActionMessage.asStateFlow()

    init {
        Log.d("MyAppTestTAG", "Test log from ArticleViewModel init")
        Log.d(TAG, "ArticleViewModel initialized.")
        fetchArticleData()
    }

    private fun fetchArticleData() {
        val articleIdArg: String? = savedStateHandle[NAV_ARG_ARTICLE_ID]
        val articleTitleArg: String? = savedStateHandle[NAV_ARG_ARTICLE_TITLE]

        Log.d(TAG, "Attempting to load article. NavArg ID: '$articleIdArg', NavArg Title: '$articleTitleArg'")
        _uiState.value = ArticleUiState(isLoading = true) // Reset UI state with loading

        viewModelScope.launch {
            var fetchedApiTitle: String? = null
            var fetchedHtmlContent: String? = null
            var fetchedImageUrl: String? = null
            var fetchError: String? = null
            var usedIdentifierForLog = "N/A"
            var determinedPageId: Int? = null

            try {
                if (articleIdArg != null && articleIdArg != "null" && articleIdArg.isNotEmpty()) {
                    try {
                        val pageIdFromArg = articleIdArg.toInt()
                        determinedPageId = pageIdFromArg
                        usedIdentifierForLog = "pageId $determinedPageId"
                        Log.d(TAG, "Fetching article data by pageId: $determinedPageId using articleRepository.getArticle")

                        val result = articleRepository.getArticle(determinedPageId, forceNetwork = false)
                            .firstOrNull { it is Result.Success || it is Result.Error }

                        when (result) {
                            is Result.Success -> {
                                val articleData = result.data
                                fetchedApiTitle = articleData.title
                                fetchedHtmlContent = articleData.htmlContent
                                fetchedImageUrl = articleData.imageUrl
                                Log.i(TAG, "Successfully fetched data by pageId: $determinedPageId. API Title: '$fetchedApiTitle'")
                            }
                            is Result.Error -> {
                                fetchError = result.message
                                Log.w(TAG, "Failed to load article content by pageId: $determinedPageId. Repository returned error: ${result.message}")
                            }
                            is Result.Loading -> {
                                Log.d(TAG, "Article fetching for pageId $determinedPageId is Loading.")
                            }
                            null -> {
                                fetchError = "Failed to load article content for ID: $determinedPageId. No data received from repository."
                                Log.w(TAG, "No Success/Error result from getArticle for pageId: $determinedPageId.")
                            }
                        }
                    } catch (e: NumberFormatException) {
                        Log.e(TAG, "Failed to parse article ID '$articleIdArg' to Int. Will try fetching by title if available.", e)
                        fetchError = "Invalid Article ID format: '$articleIdArg'."
                        determinedPageId = null
                    }
                }

                if ((determinedPageId == null || fetchError != null || fetchedHtmlContent == null) &&
                    !articleTitleArg.isNullOrEmpty() && articleTitleArg != "null"
                ) {
                    val idFetchAttempted = articleIdArg != null && articleIdArg != "null" && articleIdArg.isNotEmpty()
                    if (fetchError != null && idFetchAttempted) {
                        Log.d(TAG, "Previous attempt with ID '$articleIdArg' resulted in error/no content ('$fetchError'). Now attempting fetch by title: '$articleTitleArg'")
                    } else if (!idFetchAttempted && (articleIdArg.isNullOrEmpty() || articleIdArg == "null")) {
                        Log.d(TAG, "No valid ID provided or attempted. Attempting fetch by title: '$articleTitleArg'")
                    }

                    usedIdentifierForLog = "title '$articleTitleArg'"
                    Log.d(TAG, "Fetching article data by title: '$articleTitleArg' using articleRepository.getArticleByTitle")

                    val resultByTitle = articleRepository.getArticleByTitle(articleTitleArg, forceNetwork = true)
                        .firstOrNull { it is Result.Success || it is Result.Error }

                    when (resultByTitle) {
                        is Result.Success -> {
                            val articleData = resultByTitle.data
                            determinedPageId = articleData.pageId
                            fetchedApiTitle = articleData.title
                            fetchedHtmlContent = articleData.htmlContent
                            fetchedImageUrl = articleData.imageUrl
                            Log.i(TAG, "Successfully fetched data by title: '$articleTitleArg'. API Title: '$fetchedApiTitle', Resolved pageId: $determinedPageId")
                            fetchError = null
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
                    if (fetchedApiTitle == null && fetchError != null) fetchedApiTitle = articleTitleArg
                } else if (fetchError == null && fetchedHtmlContent == null) {
                    if ((articleIdArg.isNullOrEmpty() || articleIdArg == "null") && (articleTitleArg.isNullOrEmpty() || articleTitleArg == "null")) {
                        fetchError = "Article identifier (ID or Title) not provided or invalid."
                        Log.e(TAG, fetchError)
                    } else {
                        Log.w(TAG, "Reached end of fetch logic with no content and no error; identifier: $usedIdentifierForLog")
                        fetchError = "Failed to load article using identifier: $usedIdentifierForLog. Reason unclear."
                    }
                }

                if (fetchError == null && fetchedHtmlContent != null) {
                    _uiState.value = ArticleUiState(
                        isLoading = false,
                        pageId = determinedPageId,
                        title = fetchedApiTitle,
                        htmlContent = fetchedHtmlContent,
                        imageUrl = fetchedImageUrl,
                        error = null
                    )
                    determinedPageId?.let { observeOfflineStatus(it) }
                } else {
                    _uiState.value = ArticleUiState(
                        isLoading = false,
                        pageId = determinedPageId,
                        title = fetchedApiTitle ?: articleTitleArg ?: if (articleIdArg != "null" && !articleIdArg.isNullOrEmpty()) articleIdArg else null,
                        htmlContent = null,
                        imageUrl = null,
                        error = fetchError ?: "An unexpected error occurred while loading the article: $usedIdentifierForLog."
                    )
                    determinedPageId?.let { observeOfflineStatus(it) }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception while fetching article data using identifier: $usedIdentifierForLog", e)
                _uiState.value = ArticleUiState(
                    isLoading = false,
                    pageId = determinedPageId,
                    title = articleTitleArg ?: if (articleIdArg != "null" && !articleIdArg.isNullOrEmpty()) articleIdArg else null,
                    error = "An error occurred: ${e.localizedMessage}"
                )
                determinedPageId?.let { observeOfflineStatus(it) }
            }
        }
    }

    private fun observeOfflineStatus(pageId: Int) {
        offlineStatusJob?.cancel()
        offlineStatusJob = viewModelScope.launch {
            articleRepository.isArticleOffline(pageId).collect { isOffline ->
                _isArticleOffline.value = isOffline
                Log.d(TAG, "Offline status for pageId $pageId updated to: $isOffline")
            }
        }
    }

fun toggleSaveOfflineStatus() {
        viewModelScope.launch {
            val currentUiStateValue = uiState.value
            val id = currentUiStateValue.pageId
            val title = currentUiStateValue.title // Title from UI state, which should be the fetched title

            if (id == null) {
                Log.w(TAG, "toggleSaveOfflineStatus: pageId is null. Cannot proceed.")
                _offlineActionMessage.value = Event(ArticleOfflineUserMessage.Error("Page ID not available to perform offline action."))
                return@launch
            }

            if (!_isArticleOffline.value && title == null) {
                Log.w(TAG, "toggleSaveOfflineStatus: title is null. Cannot save article (pageId: $id) without a title.")
                _offlineActionMessage.value = Event(ArticleOfflineUserMessage.Error("Article title not available to save for offline use."))
                return@launch
            }

            if (_isArticleOffline.value) { // Current state is "Saved", so action is to "Remove"
                Log.d(TAG, "Attempting to remove offline article pageId: $id")
                when (val result = articleRepository.removeArticleOffline(id)) {
                    is Result.Success -> {
                        Log.d(TAG, "Successfully removed article pageId: $id from offline storage.")
                        _offlineActionMessage.value = Event(ArticleOfflineUserMessage.Success("Article removed from offline storage."))
                    }
                    is Result.Error -> {
                        Log.e(TAG, "Failed to remove article pageId: $id. Error: ${result.message}")
                        _offlineActionMessage.value = Event(ArticleOfflineUserMessage.Error("Failed to remove: ${result.message}"))
                    }
                    is Result.Loading -> { // ADDED: Exhaustive branch
                        Log.w(TAG, "Unexpected Result.Loading when trying to remove article pageId: $id.")
                        // Optionally, provide feedback for this unexpected state
                        _offlineActionMessage.value = Event(ArticleOfflineUserMessage.Error("An unexpected loading state occurred while removing."))
                    }
                }
            } else { // Current state is "Not Saved", so action is to "Save"
                Log.d(TAG, "Attempting to save article offline pageId: $id, title: ${title!!}")
                when (val result = articleRepository.saveArticleOffline(id, title)) { // title is non-null here
                    is Result.Success -> {
                        Log.d(TAG, "Successfully saved article pageId: $id, title: $title for offline use.")
                        _offlineActionMessage.value = Event(ArticleOfflineUserMessage.Success("Article saved for offline use."))
                    }
                    is Result.Error -> {
                        Log.e(TAG, "Failed to save article pageId: $id, title: $title. Error: ${result.message}")
                        _offlineActionMessage.value = Event(ArticleOfflineUserMessage.Error("Failed to save: ${result.message}"))
                    }
                    is Result.Loading -> { // ADDED: Exhaustive branch
                        Log.w(TAG, "Unexpected Result.Loading when trying to save article pageId: $id.")
                        // Optionally, provide feedback for this unexpected state
                        _offlineActionMessage.value = Event(ArticleOfflineUserMessage.Error("An unexpected loading state occurred while saving."))
                    }
                }
            }
        }
    }

    @Suppress("unused") // For upcoming pull-to-refresh feature
    fun refreshArticle() {
        Log.d(TAG, "refreshArticle() called. Re-fetching data.")
        offlineStatusJob?.cancel()
        _isArticleOffline.value = false
        fetchArticleData()
    }
}
