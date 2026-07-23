package com.omiyawaki.osrswiki.page

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidRandomLinkRoutingAuditTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun manifestRoutesConformToAndroidRoutingPolicy() {
        val manifestPath = System.getProperty("osrs.audit.manifest")
            ?: System.getenv("OSRS_AUDIT_MANIFEST")
            ?: ""
        assumeTrue("Set osrs.audit.manifest to run the random link routing audit replay.", manifestPath.isNotBlank())

        val manifest = File(manifestPath)
        assumeTrue("Audit manifest exists at $manifestPath", manifest.isFile)

        val resultsPath = System.getProperty("osrs.audit.results")
            ?: System.getenv("OSRS_AUDIT_RESULTS")
        val results = resultsPath?.let { File(it) }
        results?.parentFile?.mkdirs()
        results?.writeText("")

        var count = 0
        manifest.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            count += 1
            val item = JsonParser.parseString(line).asJsonObject
            val sampleId = item["sample_id"].asString
            val routingInput = item["routing_input"].asString
            val expectedRoute = expectedRouteForPolicy(Uri.parse(routingInput))
            val capture = CapturingLinkHandler(context)

            capture.processUri(Uri.parse(routingInput))

            assertEquals("Route mismatch for $sampleId $routingInput", expectedRoute, capture.actualRoute)
            results?.appendText(
                """{"sample_id":"$sampleId","routing_input":${jsonString(routingInput)},"expected_route":"$expectedRoute","actual_route":"${capture.actualRoute}","actual_article_title":${jsonString(capture.articleTitle)}}""" + "\n"
            )
        }

        assumeTrue("Audit manifest contains samples", count > 0)
    }

    private fun expectedRouteForPolicy(uri: Uri): String {
        if (!"oldschool.runescape.wiki".equals(uri.host, ignoreCase = true)) {
            return "external"
        }

        val path = uri.path ?: return "external"
        if (path.startsWith("/images/") || path.startsWith("/thumb/")) {
            return "external"
        }
        val action = uri.getQueryParameter("action")
        if (action != null && !action.equals("view", ignoreCase = true)) {
            return "external"
        }

        val title = when {
            path.startsWith("/w/") && path.length > "/w/".length -> path.substring("/w/".length)
            path.startsWith("/wiki/") && path.length > "/wiki/".length -> path.substring("/wiki/".length)
            path == "/index.php" -> uri.getQueryParameter("title")
            else -> null
        }

        return if (title != null && isMainNamespaceArticleTitle(title)) {
            "app_article_viewer"
        } else {
            "external"
        }
    }

    private fun isMainNamespaceArticleTitle(title: String): Boolean {
        if (title.isBlank()) {
            return false
        }
        val namespace = title
            .substringBefore(':', missingDelimiterValue = "")
            .replace('_', ' ')
            .lowercase()
        return namespace.isBlank() || namespace !in NON_ARTICLE_NAMESPACES
    }

    private fun jsonString(value: String?): String {
        return if (value == null) {
            "null"
        } else {
            "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\""
        }
    }

    private class CapturingLinkHandler(context: Context) : LinkHandler(context) {
        var actualRoute: String? = null
        var articleTitle: String? = null

        override fun onInternalArticleLinkClicked(articleTitle: String, fullUri: Uri) {
            actualRoute = "app_article_viewer"
            this.articleTitle = articleTitle.replace('_', ' ')
        }

        override fun onExternalLinkClicked(uri: Uri) {
            actualRoute = "external"
        }
    }

    companion object {
        private val NON_ARTICLE_NAMESPACES = setOf(
            "category",
            "file",
            "help",
            "image",
            "media",
            "mediawiki",
            "module",
            "project",
            "runescape",
            "special",
            "talk",
            "template",
            "user",
            "user talk",
            "wikipedia",
        )
    }
}
