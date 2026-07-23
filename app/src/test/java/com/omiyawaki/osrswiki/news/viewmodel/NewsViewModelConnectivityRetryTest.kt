package com.omiyawaki.osrswiki.news.viewmodel

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.news.model.AnnouncementItem
import com.omiyawaki.osrswiki.news.model.OnThisDayItem
import com.omiyawaki.osrswiki.news.model.PopularPageItem
import com.omiyawaki.osrswiki.news.model.UpdateItem
import com.omiyawaki.osrswiki.news.model.WikiFeed
import com.omiyawaki.osrswiki.news.repository.NewsFeedRepository
import com.omiyawaki.osrswiki.news.ui.FeedItem
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import org.junit.runner.Description
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NewsViewModelConnectivityRetryTest {

    @get:Rule
    val instantExecutor = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun failedRefreshRetriesAutomaticallyWhenConnectivityReturns() = runTest {
        val isOnline = MutableStateFlow(false)
        val recoveredFeed = wikiFeedWithUpdate("Recovered update")
        val repository = FakeNewsFeedRepository(
            listOf(
                Result.failure(IOException("Unable to resolve host \"oldschool.runescape.wiki\"")),
                Result.success(recoveredFeed)
            )
        )
        val viewModel = NewsViewModel(
            application = ApplicationProvider.getApplicationContext(),
            newsRepository = repository,
            networkStatus = isOnline
        )

        viewModel.refreshNews()
        advanceUntilIdle()

        assertEquals(listOf(true), repository.forceRefreshCalls)
        assertEquals(
            "Failed to refresh Home. Please check your connection and try again.",
            viewModel.error.value
        )

        isOnline.value = true
        advanceUntilIdle()

        assertEquals(listOf(true, true), repository.forceRefreshCalls)
        assertNull(viewModel.error.value)
        val updates = viewModel.feedItems.value?.filterIsInstance<FeedItem.Updates>()?.single()
        assertEquals("Recovered update", updates?.items?.single()?.title)
    }

    @Test
    fun exposesApplicationConstructorForAndroidViewModelFactory() {
        val constructor = NewsViewModel::class.java.getConstructor(Application::class.java)

        assertNotNull(constructor)
    }

    private fun wikiFeedWithUpdate(title: String) = WikiFeed(
        recentUpdates = listOf(
            UpdateItem(
                title = title,
                snippet = "Recovered automatically",
                imageUrl = "",
                articleUrl = "https://oldschool.runescape.wiki/w/$title"
            )
        ),
        announcements = listOf(AnnouncementItem("Today", "Announcement")),
        onThisDay = OnThisDayItem("On this day", listOf("Event")),
        popularPages = listOf(PopularPageItem("Popular", "https://oldschool.runescape.wiki/w/Popular"))
    )

    private class FakeNewsFeedRepository(
        outcomes: List<Result<WikiFeed>>
    ) : NewsFeedRepository {
        private val outcomes = ArrayDeque(outcomes)
        val forceRefreshCalls = mutableListOf<Boolean>()

        override fun initialize(context: Context) = Unit

        override fun getCachedFeedSynchronously(): WikiFeed? = null

        override val isCacheValid: Boolean = false

        override fun getLastUpdatedString(): String = "Just now"

        override fun markRefreshAttempt() = Unit

        override suspend fun getWikiFeed(forceRefresh: Boolean): Result<WikiFeed> {
            forceRefreshCalls += forceRefresh
            return outcomes.removeFirst()
        }
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = StandardTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
