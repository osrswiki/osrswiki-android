package com.omiyawaki.osrswiki.page.preemptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticlePrewarmIntegrationContractTest {
    @Test
    fun listProducersAndPageFragmentUseTheSameApplicationOwnedDownloader() {
        val app = source("OSRSWikiApp.kt")
        val page = source("page/PageFragment.kt")

        assertTrue(app.contains("PageAssetDownloader(okHttpClient, pageRepository, applicationScope)"))
        assertTrue(page.contains("val pageAssetDownloader = app.pageAssetDownloader"))
        assertFalse(page.contains("PageAssetDownloader(OkHttpClientFactory"))
        listOf(
            "news/ui/NewsFragment.kt",
            "search/SearchResultsFragment.kt",
            "readinglist/ui/SavedPagesFragment.kt",
            "history/HistoryFragment.kt"
        ).forEach { path ->
            val producer = source(path)
            assertTrue("Missing visible-row dwell binder in $path", producer.contains("VisibleArticlePrewarmBinder("))
            assertTrue(
                "Missing app-owned prewarm in $path",
                producer.contains("pageAssetDownloader::prewarmArticle") ||
                    producer.contains("pageAssetDownloader.prewarmArticle")
            )
        }
        val homeAdapter = source("news/ui/NewsFeedAdapter.kt")
        assertTrue(homeAdapter.contains("fun prewarmCandidatesAt"))
        assertTrue(homeAdapter.contains("visibleRecyclerPositions"))
        assertTrue(homeAdapter.contains("visibleLinearChildIndices"))
        assertTrue(homeAdapter.contains("child.isClickable = true"))
        assertFalse(homeAdapter.contains("child.isClickable = fullyVisible"))
        assertTrue(homeAdapter.contains("nestedRecyclerView.clipChildren = false"))
    }

    @Test
    fun preparedPayloadIsModeIndependentAndNoEagerMediaCompetesWithFirstPaint() {
        val downloader = source("page/PageAssetDownloader.kt")
        val preparedText = downloader.substringAfter("private suspend fun processPreparedText(")
            .substringBefore("private fun logPreparationFailure")

        assertFalse(preparedText.contains("extractAssetUrls"))
        assertTrue(preparedText.contains("backgroundUrls = emptyList()"))
        assertTrue(downloader.contains("send(DownloadProgress.Success(result))"))
        assertFalse(downloader.contains("downloadPostTextPriorityAssets"))
        assertFalse(downloader.contains("downloadBackgroundAssets"))

        val loader = source("page/PageContentLoader.kt")
        val successBranch = loader.substringAfter("is DownloadProgress.Success ->")
            .substringBefore("is DownloadProgress.Failure ->")
        assertFalse(successBranch.contains("downloadPostTextPriorityAssets"))
        assertTrue(successBranch.contains("WebView owns visible media after the document commit"))
        assertFalse(successBranch.contains("startLiveArticleAssetWarm"))

        val fragment = source("page/PageFragment.kt")
        val readyCallback = fragment.substringAfter("override fun onPageReadyForDisplay()")
            .substringBefore("fun showFindInPage()")
        assertTrue(readyCallback.contains("finalizeAndRevealPage"))
        assertTrue(readyCallback.contains("startLiveArticleAssetWarm"))
        assertTrue(
            readyCallback.indexOf("finalizeAndRevealPage") <
                readyCallback.indexOf("startLiveArticleAssetWarm")
        )
        assertFalse(preparedText.contains("startLiveArticleAssetWarm"))
        assertFalse(downloader.contains("osrsLiveArticleAssetWarmer"))
        assertTrue(downloader.contains("osrsFirstViewAssetWarmer"))
        assertTrue(downloader.contains("cancelFirstViewIfUnpinned"))
        assertTrue(downloader.contains("startFirstViewPaint"))
        assertTrue(downloader.contains("osrsPreparedArticleWebViewStore.cancel"))
        assertTrue(downloader.contains("disableFirstViewPaintPrewarm"))
        assertTrue(downloader.contains("pinFirstView"))
        assertTrue(downloader.contains("osrsPreparedArticleWebViewStore.pin"))
        assertTrue(downloader.contains("osrsPreparedArticleWebViewStore.unpin"))
        assertFalse(downloader.contains("FIRST_SCREEN_LIMIT"))
        val page = source("page/PageFragment.kt")
        assertTrue(page.contains("adoptPreparedArticleWebViewIfReady"))
        assertTrue(page.contains("osrsPreparedArticleWebViewStore.take"))
        assertTrue(page.contains("osrsNotifyFirstViewComplete"))
        val uiUpdater = source("page/PageUiUpdater.kt")
        assertTrue(uiUpdater.contains("skipInitialRender"))
        assertTrue(uiUpdater.contains("skipping loadDataWithBaseURL"))
        val webViewManager = source("page/PageWebViewManager.kt")
        assertTrue(webViewManager.contains("markAdoptedDocumentReady"))
        val store = source("page/osrsPreparedArticleWebViewStore.kt")
        assertTrue(store.contains("MutableContextWrapper"))
        assertTrue(store.contains("osrsFirstViewPaintWarm: done"))
        assertTrue(store.contains("evictIfNeeded(admitting"))
        assertTrue(store.contains("preferredPins"))
        assertTrue(store.contains("markPreferred"))
        assertTrue(store.contains("cap is full of pinned"))
        assertTrue(store.contains("!it.isPainted"))
        assertTrue(store.contains("translationX = 1f"))
        assertTrue(store.contains("dispatchTouchEvent"))
        assertTrue(store.contains("isFocusable = false"))
        assertTrue(store.contains("pollPainted"))
        assertTrue(store.contains("unpinForeground"))
        val binder = source("page/preemptive/VisibleArticlePrewarmBinder.kt")
        assertTrue(binder.contains("osrsPreparedArticleWebViewStore.rememberHost"))
        assertTrue(binder.contains("additionalCandidates"))
        val searchResults = source("search/SearchResultsFragment.kt")
        assertTrue(searchResults.contains("additionalCandidates"))
        assertTrue(searchResults.contains("exactQueryPrewarmCandidate"))
        assertTrue(searchResults.contains("markPreferred"))
        val searchActivity = source("search/SearchActivity.kt")
        assertTrue(searchActivity.contains("EXTRA_DISABLE_FIRST_VIEW_PAINT_PREWARM"))
        assertTrue(searchActivity.contains("disableFirstViewPaintPrewarm"))
        val activity = source("page/PageActivity.kt")
        assertTrue(activity.contains("pinFirstView"))
        assertTrue(loader.contains("pendingFirstViewComplete"))
        assertTrue(loader.contains("firstViewCompletePosted"))
    }

    @Test
    fun networkCallbacksNotifyRuntimeSuppressionGate() {
        val app = source("OSRSWikiApp.kt")
        assertTrue(app.contains("override fun onAvailable"))
        assertTrue(app.contains("override fun onLost"))
        assertTrue(app.contains("override fun onCapabilitiesChanged"))
        assertTrue(app.contains("pageAssetDownloader.notifyPrewarmEnvironmentChanged()"))
        assertTrue(app.contains("updateNetworkStatus("))
    }

    private fun source(path: String): String = listOf(
        File("src/main/java/com/omiyawaki/osrswiki", path),
        File("app/src/main/java/com/omiyawaki/osrswiki", path)
    ).firstOrNull(File::exists)?.readText() ?: error("Missing source: $path")
}
