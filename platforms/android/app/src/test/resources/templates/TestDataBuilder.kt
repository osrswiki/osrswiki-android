package {{PACKAGE_NAME}}

/**
 * Test data builder for creating consistent test fixtures
 * Usage: TestDataBuilder.createSamplePageTitle(title = "Custom Title")
 */
object TestDataBuilder {

    fun createSamplePageTitle(
        title: String = "Test Page",
        namespace: Int = 0,
        wikiSite: WikiSite = createSampleWikiSite()
    ): PageTitle {
        return PageTitle(title, namespace, wikiSite)
    }

    fun createSampleWikiSite(
        languageCode: String = "en",
        domain: String = "oldschool.runescape.wiki"
    ): WikiSite {
        return WikiSite.forLanguageCode(languageCode)
    }

    fun createSampleSearchResult(
        title: String = "Test Result",
        description: String = "Test description",
        thumbnail: String? = null
    ): SearchResult {
        return SearchResult(title, description, thumbnail)
    }

    fun createSampleSearchResponse(
        results: List<SearchResult> = listOf(createSampleSearchResult())
    ): SearchApiResponse {
        return SearchApiResponse(
            query = SearchQuery(search = results)
        )
    }

    fun createSampleHistoryEntry(
        title: String = "Test Page",
        timestamp: Long = System.currentTimeMillis(),
        source: Int = 0
    ): HistoryEntry {
        return HistoryEntry(
            title = createSamplePageTitle(title),
            timestamp = timestamp,
            source = source
        )
    }

    fun createSampleReadingListPage(
        title: String = "Test Page",
        description: String? = "Test description",
        thumbUrl: String? = null
    ): ReadingListPage {
        return ReadingListPage(
            wiki = createSampleWikiSite(),
            namespace = 0,
            displayTitle = title,
            description = description,
            thumbUrl = thumbUrl
        )
    }

    fun createSampleException(
        message: String = "Test error"
    ): Exception {
        return RuntimeException(message)
    }

    fun createSampleNetworkException(
        message: String = "Network error"
    ): Exception {
        return java.io.IOException(message)
    }

    // Common test data constants
    object Constants {
        const val SAMPLE_WIKI_URL = "https://oldschool.runescape.wiki/w/Test_Page"
        const val SAMPLE_SEARCH_QUERY = "dragon"
        const val SAMPLE_USER_AGENT = "OSRSWiki/Test"
        const val SAMPLE_TIMESTAMP = 1642681200000L // 2022-01-20 15:00:00 UTC
    }
}