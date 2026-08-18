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
            assertTrue("Missing app-owned prewarm in $path", producer.contains("pageAssetDownloader::prewarmArticle"))
        }
        val homeAdapter = source("news/ui/NewsFeedAdapter.kt")
        assertTrue(homeAdapter.contains("fun prewarmCandidatesAt"))
        assertTrue(homeAdapter.contains("visibleRecyclerPositions"))
        assertTrue(homeAdapter.contains("visibleLinearChildIndices"))
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
