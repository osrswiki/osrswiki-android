package com.omiyawaki.osrswiki.page

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LinkHandlerRoutingPolicyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun mainNamespaceArticleLinksRouteToAppArticleViewer() {
        val cases = listOf(
            Case("/w absolute", "https://oldschool.runescape.wiki/w/Dragon_scimitar", "Dragon_scimitar"),
            Case("/w relative", "/w/Abyssal_whip", "Abyssal_whip"),
            Case("index.php title", "https://oldschool.runescape.wiki/index.php?title=Vorkath", "Vorkath"),
            Case("legacy /wiki path", "https://oldschool.runescape.wiki/wiki/Zulrah", "Zulrah"),
            Case("fragment", "https://oldschool.runescape.wiki/w/Barrows#Rewards", "Barrows"),
            Case("oldid query", "https://oldschool.runescape.wiki/index.php?title=Barrows&oldid=1", "Barrows"),
            Case("percent encoded title", "https://oldschool.runescape.wiki/w/Dragon%20scimitar", "Dragon scimitar"),
        )

        cases.forEach { case ->
            val capture = CapturingLinkHandler(context)

            capture.processUri(Uri.parse(case.uri))

            assertEquals("${case.name} route", "app_article_viewer", capture.actualRoute)
            assertEquals("${case.name} title", case.expectedArticleTitle, capture.articleTitle)
        }
    }

    @Test
    fun nonArticleNamespacesRouteToExternalOrSpecialFallback() {
        val cases = listOf(
            Case("file namespace", "https://oldschool.runescape.wiki/w/File:Abyssal_whip_detail.png"),
            Case("media namespace", "https://oldschool.runescape.wiki/w/Media:Abyssal_whip_detail.png"),
            Case("special namespace", "https://oldschool.runescape.wiki/w/Special:Random"),
            Case("template namespace", "https://oldschool.runescape.wiki/w/Template:Blackjack"),
            Case("talk namespace", "https://oldschool.runescape.wiki/w/Talk:Zulrah"),
            Case("project namespace", "https://oldschool.runescape.wiki/w/RuneScape:Drop_Rate_Project"),
        )

        cases.forEach { case ->
            val capture = CapturingLinkHandler(context)

            capture.processUri(Uri.parse(case.uri))

            assertEquals("${case.name} route", "external", capture.actualRoute)
            assertEquals("${case.name} title", null, capture.articleTitle)
        }
    }

    @Test
    fun nonViewActionsRouteToExternalOrSpecialFallback() {
        val cases = listOf(
            Case("edit action /w", "https://oldschool.runescape.wiki/w/Woodcutting_stump?action=edit&section=3"),
            Case("purge action /w", "https://oldschool.runescape.wiki/w/Money_making_guide/Smithing_mithril_dart_tips?action=purge"),
            Case("history action index", "https://oldschool.runescape.wiki/index.php?title=Zulrah&action=history"),
        )

        cases.forEach { case ->
            val capture = CapturingLinkHandler(context)

            capture.processUri(Uri.parse(case.uri))

            assertEquals("${case.name} route", "external", capture.actualRoute)
            assertEquals("${case.name} title", null, capture.articleTitle)
        }
    }

    @Test
    fun nonWikiLinksRouteExternally() {
        val capture = CapturingLinkHandler(context)

        capture.processUri(Uri.parse("https://www.jagex.com/en-GB/"))

        assertEquals("external", capture.actualRoute)
        assertEquals(null, capture.articleTitle)
    }

    private data class Case(
        val name: String,
        val uri: String,
        val expectedArticleTitle: String? = null,
    )

    private class CapturingLinkHandler(context: Context) : LinkHandler(context) {
        var actualRoute: String? = null
        var articleTitle: String? = null

        override fun onInternalArticleLinkClicked(articleTitle: String, fullUri: Uri) {
            actualRoute = "app_article_viewer"
            this.articleTitle = articleTitle
        }

        override fun onExternalLinkClicked(uri: Uri) {
            actualRoute = "external"
        }
    }
}
