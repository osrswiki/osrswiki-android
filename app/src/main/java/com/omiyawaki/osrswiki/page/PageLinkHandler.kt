package com.omiyawaki.osrswiki.page

import android.content.Context // Ensure Context is imported
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.omiyawaki.osrswiki.page.PageViewModel
import com.omiyawaki.osrswiki.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles clicks on links within a WebView by implementing LinkHandler's abstract methods.
 *
 * @param constructionContext The context passed during construction, used for the LinkHandler base class.
 * @param coroutineScope The CoroutineScope to launch async operations.
 * @param pageRepository The repository to fetch page data.
 * @param pageViewModel The ViewModel associated with the current page, used to determine its offline status.
 */
class PageLinkHandler(
    constructionContext: Context, // This is supplied by PageFragment (e.g., requireContext())
    private val coroutineScope: CoroutineScope,
    private val pageRepository: PageRepository,
    private val pageViewModel: PageViewModel
) : LinkHandler(constructionContext) { // Pass the context to the LinkHandler base class

    // Note: Inside this class, 'this.context' or simply 'context' will refer to the
    // 'protected val context: Context' inherited from LinkHandler.

    companion object {
        private const val TAG = "PageLinkHandler"
    }

    override fun onInternalArticleLinkClicked(articleTitleWithUnderscores: String, fullUri: Uri) {
        val targetPageTitleWithSpaces = articleTitleWithUnderscores.replace('_', ' ')
        val isCurrentPageActuallyOffline = pageViewModel.uiState.isCurrentlyOffline

        Log.d(TAG, "Handling internal link. Target title: '$targetPageTitleWithSpaces' (was '$articleTitleWithUnderscores'), Current page is offline: $isCurrentPageActuallyOffline, URI: $fullUri")

        coroutineScope.launch {
            pageRepository.getArticleByTitle(targetPageTitleWithSpaces, forceNetwork = false)
                .collectLatest { result ->
                    when (result) {
                        is Result.Success -> {
                            val loadedUiState = result.data
                            if (loadedUiState.isCurrentlyOffline) {
                                Log.i(TAG, "Link target '$targetPageTitleWithSpaces' (pageId ${loadedUiState.pageId}) is available offline. Navigating.")
                                // Use inherited 'context'
                                context.startActivity(PageActivity.newIntent(context, loadedUiState.plainTextTitle, loadedUiState.pageId?.toString()))
                            } else {
                                if (isCurrentPageActuallyOffline) {
                                    Log.i(TAG, "Link target '$targetPageTitleWithSpaces' is NOT available offline, but current page IS. Notifying user.")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Page '${loadedUiState.plainTextTitle ?: targetPageTitleWithSpaces}' is not available offline.", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Log.i(TAG, "Link target '$targetPageTitleWithSpaces' is NOT available offline. Current page is ONLINE. Navigating online.")
                                    context.startActivity(PageActivity.newIntent(context, targetPageTitleWithSpaces, null))
                                }
                            }
                        }
                        is Result.Error -> {
                            Log.w(TAG, "Error loading link target '$targetPageTitleWithSpaces': ${result.message}")
                            if (isCurrentPageActuallyOffline) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Page '$targetPageTitleWithSpaces' is not available offline and could not be loaded.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Log.w(TAG, "Error loading link target '$targetPageTitleWithSpaces' from an ONLINE page. Attempting navigation by title.")
                                context.startActivity(PageActivity.newIntent(context, targetPageTitleWithSpaces, null))
                            }
                        }
                        is Result.Loading -> {
                            Log.d(TAG, "Loading link target '$targetPageTitleWithSpaces'...")
                        }
                    }
                }
        }
    }

    override fun onExternalLinkClicked(uri: Uri) {
        Log.d(TAG, "Handling external link: $uri")
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Use inherited 'context'
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Log.w(TAG, "No application can handle this external link: $uri")
                Toast.makeText(context, "No application can handle this link.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not handle external link $uri", e)
            Toast.makeText(context, "Error opening link.", Toast.LENGTH_SHORT).show()
        }
    }
}