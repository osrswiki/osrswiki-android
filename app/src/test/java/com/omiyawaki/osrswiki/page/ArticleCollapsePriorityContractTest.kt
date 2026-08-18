package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleCollapsePriorityContractTest {

    @Test
    fun collapseTransformerUsesSemanticRolesAndMetricsWithoutArticleTitleGates() {
        val source = assetFile("web/collapsible_content.js").readText()

        assertTrue(source.contains("directRecipeTables"))
        assertTrue(source.contains("recipeRoleForTable"))
        assertTrue(source.contains("recipeDescriptor"))
        assertTrue(source.contains("dataset.osrsTableRole"))
        assertTrue(source.contains("collectCollapseMetrics"))
        assertTrue(source.contains("window.OSRSCollapseMetrics"))
        assertTrue(source.contains("dataset.collapseLabelKind"))
        assertFalse(source.contains("getArticleContext"))
        assertFalse(source.contains("isTaskCriticalTable"))
        assertFalse(source.contains("isGiantTaskTable"))
    }

    @Test
    fun structuralRulesCoverContentFamiliesWithoutNamingArticles() {
        val source = assetFile("web/collapsible_content.js").readText()

        assertTrue(source.contains("table.infobox"))
        assertTrue(source.contains("classList.contains('infobox-bonuses')"))
        assertTrue(source.contains("table.questdetails"))
        assertTrue(source.contains("table.mw-collapsible"))
        assertTrue(source.contains("table.navbox"))
        assertTrue(source.contains("table.wikitable"))
        assertFalse(source.contains("Money making guide/"))
        assertFalse(source.contains("Calculator:"))
        assertFalse(source.contains("Trailblazer Reloaded League/Tasks"))
        assertFalse(source.contains("Pay-to-play"))
    }

    @Test
    fun collapseTransformerIncludesQuestdetailsAndExplicitMwCollapsibleTables() {
        val source = assetFile("web/collapsible_content.js").readText()

        assertTrue(source.contains("table.questdetails"))
        assertTrue(source.contains("table.mw-collapsible"))
        assertTrue(source.contains("shouldTransformQuestDetailsTable"))
        assertTrue(source.contains("shouldTransformExplicitCollapsibleTable"))
        assertTrue(source.contains("collapsible-questdetails"))
        assertTrue(source.contains("collapsible-explicit-mw-collapsible"))
    }

    @Test
    fun fixtureContractsCoverExplicitAuthoredStructuresWithoutTitlePriority() {
        val questDetails = testResource("article-collapse-priority/blood-moon-questdetails.html").readText()
        val explicitCollapsible = testResource("article-collapse-priority/explicit-mw-collapsible.html").readText()

        assertTrue(questDetails.contains("class=\"questdetails\""))
        assertTrue(questDetails.indexOf("Details") < questDetails.indexOf("questdetails"))
        assertTrue(explicitCollapsible.contains("class=\"mw-collapsible mw-collapsed\""))
        assertTrue(explicitCollapsible.contains("id=\"alreadyWrapped\""))
    }

    private fun assetFile(path: String): File {
        return listOf(
            File("src/main/assets", path),
            File("app/src/main/assets", path)
        ).firstOrNull { it.exists() } ?: error("Missing Android asset: $path")
    }

    private fun testResource(path: String): File {
        return listOf(
            File("src/test/resources", path),
            File("app/src/test/resources", path)
        ).firstOrNull { it.exists() } ?: error("Missing Android test resource: $path")
    }
}
