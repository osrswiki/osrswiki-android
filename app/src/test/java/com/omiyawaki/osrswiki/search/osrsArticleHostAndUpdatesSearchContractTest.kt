package com.omiyawaki.osrswiki.search

import androidx.paging.PagingSource
import com.omiyawaki.osrswiki.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsArticleHostAndUpdatesSearchContractTest {

    @Test
    fun overlaySlidesOpaqueChromeAndKeepsLiveUnderlayVisible() {
        val overlay = source("page/osrsArticleOverlayFragment.kt")
        val presenter = source("page/osrsArticleOverlayPresenter.kt")
        val pageLayout = resource("layout/activity_page.xml")

        assertTrue(overlay.contains("fun slidingChrome(): View? = _binding?.navMenuTriggerLayout"))
        assertTrue(overlay.contains("applyOpaqueArticleChrome"))
        assertTrue(overlay.contains("binding.navMenuTriggerLayout.setBackgroundColor(typed.data)"))
        assertFalse(overlay.contains("val sliding = binding.root"))
        assertTrue(presenter.contains("elevation = 0f"))
        assertTrue(presenter.contains("fun detachHost"))
        assertTrue(pageLayout.contains("android:id=\"@+id/nav_menu_trigger_layout\""))
        assertTrue(pageLayout.contains("android:background=\"?attr/paper_color\""))
        assertTrue(pageLayout.contains("android:id=\"@+id/page_live_underlay\""))
    }

    @Test
    fun overlayDoesNotStackWhenAnArticleOverlayIsAlreadyShowing() {
        val presenter = source("page/osrsArticleOverlayPresenter.kt")
        val present = presenter.substringAfter("fun present(context: Context, intent: Intent): Boolean")
            .substringBefore("fun pop(activity: FragmentActivity): Boolean")
        assertTrue(present.contains("if (topFragment(activity) != null)"))
        assertTrue(present.contains("return false"))
    }

    @Test
    fun viewMoreOpensReusableUpdatesSearchScope() {
        val news = source("news/ui/NewsFragment.kt")
        val adapter = source("news/ui/NewsFeedAdapter.kt")
        val layout = resource("layout/item_news_card_updates.xml")
        val strings = resource("values/strings.xml")
        val activity = source("search/SearchActivity.kt")
        val fragment = source("search/SearchFragment.kt")
        val viewModel = source("search/SearchViewModel.kt")
        val api = source("network/WikiApiService.kt")
        val scoped = source("search/osrsScopedSearchPagingSource.kt")
        val scope = source("search/osrsSearchScope.kt")

        assertTrue(layout.contains("@+id/home_updates_view_more"))
        assertTrue(layout.contains("@string/home_updates_view_more"))
        assertTrue(layout.contains("?attr/linkColor"))
        assertTrue(fragment.contains("emptyQueryBrowsesNewest"))
        assertTrue(activity.contains("emptyQueryBrowsesNewest"))
        assertTrue(scoped.contains("enrichMissingPreviews"))
        assertTrue(strings.contains("home_updates_view_more\">View more<"))
        assertTrue(strings.contains("search_updates_hint\">Search updates<"))
        assertTrue(adapter.contains("onViewMoreUpdatesClicked"))
        assertTrue(news.contains("osrsSearchScope.UPDATES"))
        assertTrue(news.contains("SearchActivity.newIntent"))
        assertTrue(activity.contains("scope: osrsSearchScope"))
        assertTrue(activity.contains("searchScope()"))
        assertTrue(fragment.contains("emptyQueryBrowsesNewest"))
        assertTrue(viewModel.contains("emptyQueryBrowsesNewest"))
        assertTrue(scope.contains("namespace = osrsMediaWikiNamespace.UPDATES"))
        assertTrue(scope.contains("const val UPDATES = 112"))
        assertTrue(api.contains("gsrnamespace"))
        assertTrue(api.contains("generator=recentchanges"))
        assertTrue(api.contains("grcnamespace"))
        assertTrue(scoped.contains("generatedRecentChanges"))
        assertTrue(scoped.contains("generatedNamespacedSearch"))
        assertTrue(scoped.contains("result.ns == scope.namespace"))
    }

    @Test
    fun updatesScopePinsNamespaceAndEmptyBrowse() {
        assertEquals(112, osrsSearchScope.UPDATES.namespace)
        assertTrue(osrsSearchScope.UPDATES.emptyQueryBrowsesNewest)
        assertEquals(R.string.search_updates_hint, osrsSearchScope.UPDATES.hintResId)
        assertFalse(osrsSearchScope.ALL.emptyQueryBrowsesNewest)
        assertEquals(null, osrsSearchScope.ALL.namespace)
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/omiyawaki/osrswiki/$relativePath").readText()

    private fun resource(relativePath: String): String =
        File("src/main/res/$relativePath").readText()
}
