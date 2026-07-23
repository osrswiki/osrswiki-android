package com.omiyawaki.osrswiki.page

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidRandomLinkRoutingDeviceProbeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun forcedAndHighRiskUrisMatchLinkHandlerRouteOnDevice() {
        val cases = listOf(
            Case("blood_moon", "https://oldschool.runescape.wiki/w/The_Blood_Moon_Rises", "app_article_viewer"),
            Case("blood_moon_quick_guide", "https://oldschool.runescape.wiki/w/The_Blood_Moon_Rises/Quick_guide", "app_article_viewer"),
            Case("relative_w_resolved", "https://oldschool.runescape.wiki/w/Abyssal_whip", "app_article_viewer"),
            Case("oldschool_w", "https://oldschool.runescape.wiki/w/Dragon_scimitar", "app_article_viewer"),
            Case("oldschool_wiki_path", "https://oldschool.runescape.wiki/wiki/Zulrah", "app_article_viewer"),
            Case("index_php_title", "https://oldschool.runescape.wiki/index.php?title=Vorkath", "app_article_viewer"),
            Case("runescape_wiki_alias", "https://runescape.wiki/w/RuneScape:About", "external"),
            Case("file_page", "https://oldschool.runescape.wiki/w/File:Abyssal_whip_detail.png", "external"),
            Case("media_asset", "https://oldschool.runescape.wiki/images/Abyssal_whip_detail.png", "external"),
            Case("special_page", "https://oldschool.runescape.wiki/w/Special:Random", "external"),
            Case("fragment", "https://oldschool.runescape.wiki/w/Barrows#Rewards", "app_article_viewer"),
            Case("query_oldid", "https://oldschool.runescape.wiki/index.php?title=Barrows&oldid=1", "app_article_viewer"),
            Case("percent_encoded", "https://oldschool.runescape.wiki/w/Dragon%20scimitar", "app_article_viewer"),
            Case("external_jagex", "https://www.jagex.com/en-GB/", "external"),
            Case("edit_action", "https://oldschool.runescape.wiki/w/Woodcutting_stump?action=edit&section=1", "external"),
            Case("template_page", "https://oldschool.runescape.wiki/w/Template:Blackjack", "external"),
        )

        cases.forEach { case ->
            val capture = CapturingLinkHandler(context)
            capture.processUri(Uri.parse(case.uri))
            assertEquals("${case.name} route", case.expectedRoute, capture.actualRoute)
        }
    }

    private data class Case(
        val name: String,
        val uri: String,
        val expectedRoute: String,
    )

    private class CapturingLinkHandler(context: Context) : LinkHandler(context) {
        var actualRoute: String? = null

        override fun onInternalArticleLinkClicked(articleTitle: String, fullUri: Uri) {
            actualRoute = "app_article_viewer"
        }

        override fun onExternalLinkClicked(uri: Uri) {
            actualRoute = "external"
        }
    }
}
