package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleCollapsePriorityContractTest {

    @Test
    fun collapseTransformerHasArticleAwarePriorityRulesAndMetrics() {
        val source = assetFile("web/collapsible_content.js").readText()

        assertTrue(source.contains("getArticleContext"))
        assertTrue(source.contains("isTaskCriticalTable"))
        assertTrue(source.contains("isGiantTaskTable"))
        assertTrue(source.contains("buildCollapsedSummary"))
        assertTrue(source.contains("collectCollapseMetrics"))
        assertTrue(source.contains("window.OSRSCollapseMetrics"))
        assertTrue(source.contains("collapsible-priority-primary"))
        assertTrue(source.contains("collapsible-priority-summary"))
        assertTrue(source.contains("collapsible-summary"))
        assertTrue(source.contains("data-collapse-label-kind"))
    }

    @Test
    fun priorityRulesNamePrimaryContentFamiliesAndSecondaryControls() {
        val source = assetFile("web/collapsible_content.js").readText()

        assertTrue(source.contains("Money making guide/"))
        assertTrue(source.contains("mmg-table"))
        assertTrue(source.contains("Calculator:"))
        assertTrue(source.contains("Trailblazer Reloaded League/Tasks"))
        assertTrue(source.contains("tbrl-tasks"))
        assertTrue(source.contains("combat-styles"))
        assertTrue(source.contains("infotable-bonuses"))
        assertTrue(source.contains("Pay-to-play"))
        assertTrue(source.contains("training"))
        assertTrue(source.contains("table.navbox"))
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
    fun fixtureContractsDistinguishTaskCriticalTablesFromNavboxes() {
        val moneyGuide = testResource("article-collapse-priority/money-making-dark-crabs.html").readText()
        val taskPage = testResource("article-collapse-priority/trailblazer-tasks.html").readText()
        val equipment = testResource("article-collapse-priority/equipment-combat.html").readText()
        val training = testResource("article-collapse-priority/ranged-training.html").readText()
        val questDetails = testResource("article-collapse-priority/blood-moon-questdetails.html").readText()
        val explicitCollapsible = testResource("article-collapse-priority/explicit-mw-collapsible.html").readText()

        assertTrue(moneyGuide.indexOf("mmg-table") < moneyGuide.indexOf("navbox"))
        assertTrue(taskPage.indexOf("wikitable lighttable sortable sticky-header qc-active tbrl-tasks") > taskPage.indexOf("Trailblazer Reloaded Guides"))
        assertTrue(equipment.indexOf("infobox-bonuses") < equipment.indexOf("combat-styles"))
        assertTrue(training.indexOf("General training notes") < training.indexOf("navbox"))
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
