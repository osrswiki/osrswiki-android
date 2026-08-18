package com.omiyawaki.osrswiki.page.preemptive

import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.page.DownloadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PreparedArticleCacheTest {
    @Test
    fun canonicalIdAndNormalizedTitleBothFindThemeIndependentResult() {
        var now = 100L
        val cache = PreparedArticleCache(maxEntries = 3, ttlMillis = 1_000L) { now }
        val request = ArticlePrewarmRequest(title = "Amulet_of_glory")
        val result = result(pageId = 123, title = "Amulet of glory")

        cache.put(request, result)

        assertSame(result, cache.get(ArticlePrewarmRequest(pageId = 123))?.value)
        assertSame(result, cache.get(ArticlePrewarmRequest(title = " amulet   of glory "))?.value)
    }

    @Test
    fun redirectRetainsRequestedAndCanonicalAliases() {
        val cache = PreparedArticleCache(maxEntries = 3, ttlMillis = 1_000L) { 100L }
        val redirected = result(pageId = 44, title = "Canonical destination")

        cache.put(ArticlePrewarmRequest(title = "Old redirect"), redirected)

        assertSame(redirected, cache.get(ArticlePrewarmRequest(title = "Old_redirect"))?.value)
        assertSame(redirected, cache.get(ArticlePrewarmRequest(title = "Canonical destination"))?.value)
        assertSame(redirected, cache.get(ArticlePrewarmRequest(pageId = 44))?.value)
    }

    @Test
    fun repeatedRedirectsMergePriorAliasesWithoutUnboundedIdentityGrowth() {
        val cache = PreparedArticleCache(maxEntries = 3, ttlMillis = 1_000L) { 100L }
        val canonical = result(pageId = 44, title = "Canonical destination")
        cache.put(ArticlePrewarmRequest(title = "Redirect one"), canonical)
        cache.put(ArticlePrewarmRequest(title = "Redirect two"), canonical)

        assertSame(canonical, cache.get(ArticlePrewarmRequest(title = "Redirect one"))?.value)
        assertSame(canonical, cache.get(ArticlePrewarmRequest(title = "Redirect two"))?.value)
        assertSame(canonical, cache.get(ArticlePrewarmRequest(pageId = 44))?.value)
        assertSame(
            canonical,
            cache.get(ArticlePrewarmRequest(title = "Canonical destination"))?.value
        )

        (1..PreparedArticleCache.MAX_KEYS_PER_ENTRY * 2).forEach { alias ->
            cache.put(ArticlePrewarmRequest(title = "Alias $alias"), canonical)
        }
        assertEquals(1, cache.sizeForTests())
        assertSame(canonical, cache.get(ArticlePrewarmRequest(pageId = 44))?.value)
    }

    @Test
    fun meaningfulCaseAndDiacriticsNeverCollide() {
        val cache = PreparedArticleCache(maxEntries = 4, ttlMillis = 1_000L) { 100L }
        val lowerCase = result(pageId = 50, title = "Rune sword")
        val accented = result(pageId = 51, title = "Élite clue")
        cache.put(ArticlePrewarmRequest(title = "Rune sword"), lowerCase)
        cache.put(ArticlePrewarmRequest(title = "Élite clue"), accented)

        assertNull(cache.get(ArticlePrewarmRequest(title = "Rune Sword")))
        assertNull(cache.get(ArticlePrewarmRequest(title = "Elite clue")))
        assertSame(accented, cache.get(ArticlePrewarmRequest(title = "élite clue"))?.value)
    }

    @Test
    fun conflictingAuthoritativeIdsNeverHitBySharedDisplayTitle() {
        val cache = PreparedArticleCache(maxEntries = 4, ttlMillis = 1_000L) { 100L }
        val first = result(pageId = 70, title = "Shared display title")
        cache.put(ArticlePrewarmRequest(pageId = 70, title = "Shared display title"), first)

        assertNull(
            cache.get(ArticlePrewarmRequest(pageId = 71, title = "Shared display title"))
        )
        assertSame(
            first,
            cache.get(ArticlePrewarmRequest(pageId = 70, title = "Changed canonical title"))?.value
        )
    }

    @Test
    fun accessOrderEvictsLeastRecentlyUsedEntry() {
        var now = 0L
        val cache = PreparedArticleCache(maxEntries = 2, ttlMillis = 1_000L) { now }
        cache.put(ArticlePrewarmRequest(pageId = 1), result(1, "One"))
        now += 1
        cache.put(ArticlePrewarmRequest(pageId = 2), result(2, "Two"))
        cache.get(ArticlePrewarmRequest(pageId = 1))
        now += 1
        cache.put(ArticlePrewarmRequest(pageId = 3), result(3, "Three"))

        assertNull(cache.get(ArticlePrewarmRequest(pageId = 2)))
        assertEquals("One", cache.get(ArticlePrewarmRequest(pageId = 1))?.value?.parseResult?.title)
        assertEquals("Three", cache.get(ArticlePrewarmRequest(pageId = 3))?.value?.parseResult?.title)
    }

    @Test
    fun staleEntriesExpireByMonotonicTtl() {
        var now = 10L
        val cache = PreparedArticleCache(maxEntries = 2, ttlMillis = 50L) { now }
        cache.put(ArticlePrewarmRequest(pageId = 1), result(1, "One"))
        now = 59L
        assertEquals(1, cache.sizeForTests())
        now = 60L
        assertNull(cache.get(ArticlePrewarmRequest(pageId = 1)))
        assertEquals(0, cache.sizeForTests())
    }

    @Test
    fun savedDeleteOrResyncInvalidatesEveryAliasButAppearanceDoesNotNeedGlobalClear() {
        val cache = PreparedArticleCache(maxEntries = 3, ttlMillis = 1_000L) { 100L }
        val saved = result(pageId = 88, title = "Fresh saved title")
        cache.put(ArticlePrewarmRequest(title = "Old redirect"), saved)

        cache.invalidate(ArticlePrewarmRequest(pageId = 88))

        assertNull(cache.get(ArticlePrewarmRequest(pageId = 88)))
        assertNull(cache.get(ArticlePrewarmRequest(title = "Fresh saved title")))
        assertNull(cache.get(ArticlePrewarmRequest(title = "Old redirect")))
    }

    private fun result(pageId: Int, title: String) = DownloadResult(
        processedHtml = "<p>$title full article body</p>",
        parseResult = ParseResult(title, pageId, 9L, "<p>body</p>", title),
        backgroundUrls = emptyList()
    )
}
