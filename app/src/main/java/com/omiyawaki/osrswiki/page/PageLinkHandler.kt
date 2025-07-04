package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageLinkHandler(
    constructionContext: Context,
    private val coroutineScope: CoroutineScope,
    private val pageRepository: PageRepository,
    private val theme: Theme
) : LinkHandler(constructionContext) {

    companion object {
        private const val TAG = "PageLinkHandler"
    }

    override fun onInternalArticleLinkClicked(articleTitleWithUnderscores: String, fullUri: Uri) {
        val targetPageTitleWithSpaces = articleTitleWithUnderscores.replace('_', ' ')
        Log.d(TAG, "Attempting to handle internal link. Target title: '$targetPageTitleWithSpaces', URI: $fullUri")

        coroutineScope.launch {
            var navigationAttempted = false
            pageRepository.getArticleByTitle(targetPageTitleWithSpaces, theme, forceNetwork = false)
                .collectLatest { result ->
                    if (navigationAttempted && (result is Result.Success || result is Result.Error)) {
                        Log.d(TAG, "Navigation already attempted for '$targetPageTitleWithSpaces', ignoring further emissions for this click.")
                        return@collectLatest
                    }

                    when (result) {
                        is Result.Success -> {
                            val loadedTargetUiState = result.data
                            if (loadedTargetUiState.isCurrentlyOffline && loadedTargetUiState.htmlContent != null) {
                                Log.i(TAG, "Target '$targetPageTitleWithSpaces' (ID: ${loadedTargetUiState.pageId}) found OFFLINE with content. Navigating offline.")
                                context.startActivity(PageActivity.newIntent(
                                    context,
                                    loadedTargetUiState.plainTextTitle,
                                    loadedTargetUiState.pageId?.toString(),
                                    HistoryEntry.SOURCE_INTERNAL_LINK
                                ))
                                navigationAttempted = true
                            } else {
                                Log.i(TAG, "Target '$targetPageTitleWithSpaces' not found as usable offline content. Checking network for online navigation.")
                                if (isNetworkAvailable()) {
                                    Log.i(TAG, "Network is available. Navigating ONLINE to '$targetPageTitleWithSpaces'.")
                                    context.startActivity(PageActivity.newIntent(
                                        context,
                                        targetPageTitleWithSpaces,
                                        null,
                                        HistoryEntry.SOURCE_INTERNAL_LINK
                                    ))
                                } else {
                                    Log.w(TAG, "Target '$targetPageTitleWithSpaces' not available offline and NO network connection.")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Page '$targetPageTitleWithSpaces' is not available for offline viewing and there is no network connection.", Toast.LENGTH_LONG).show()
                                    }
                                }
                                navigationAttempted = true
                            }
                        }
                        is Result.Error -> {
                            Log.w(TAG, "Error initially fetching target '$targetPageTitleWithSpaces' from repository (forceNetwork=false): ${result.message}")
                            if (isNetworkAvailable()) {
                                Log.i(TAG, "Network is available. Attempting ONLINE navigation to '$targetPageTitleWithSpaces' due to previous error loading from cache.")
                                context.startActivity(PageActivity.newIntent(
                                    context,
                                    targetPageTitleWithSpaces,
                                    null,
                                    HistoryEntry.SOURCE_INTERNAL_LINK
                                ))
                            } else {
                                Log.w(TAG, "Error fetching '$targetPageTitleWithSpaces' and NO network connection.")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Could not load page '$targetPageTitleWithSpaces'. No network connection.", Toast.LENGTH_LONG).show()
                                }
                            }
                            navigationAttempted = true
                        }
                        is Result.Loading -> {
                            Log.d(TAG, "Loading link target '$targetPageTitleWithSpaces' from repository...")
                        }
                    }
                }
        }
    }

    override fun onExternalLinkClicked(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    @SuppressLint("MissingPermission")
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                return networkInfo?.isConnected == true
            }
        }
        return false
    }
}
