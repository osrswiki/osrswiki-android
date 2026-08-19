package com.omiyawaki.osrswiki.page

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reduced, deterministic DOM contracts taken from the article structures named in QA. */
class ArticleRealSemanticFixtureContractTest {
    @Test
    fun barbarianLoreAndFaladorTransportStoreIconsRemainPhrasingContent() {
        val barbarian = fixture("barbarian-village-inline.html")
        val loreParagraph = barbarian.selectFirst("p.lore-note")!!
        val loreIcon = loreParagraph.selectFirst("img.mw-file-element")!!
        assertSame(loreParagraph, loreIcon.closest("p"))
        assertTrue(loreParagraph.text().contains("village's history"))
        assertTrue(loreIcon.attr("width").toInt() <= 48)
        assertTrue(loreIcon.attr("height").toInt() <= 48)

        val sourced = barbarian.selectFirst("#lore-sources")!!
        val sourcedGroup = sourced.selectFirst("span")!!
        assertTrue(sourcedGroup.html().contains("<i>"))
        assertTrue(sourced.text().contains("Varrock Museum"))
        assertNotNull(sourcedGroup.selectFirst("img.mw-file-element"))

        val falador = fixture("falador-inline.html")
        val sampledIcons = falador.select("#Transportation + p img.mw-file-element, #Stores + ul img.mw-file-element")
        assertEquals(3, sampledIcons.size)
        assertTrue(sampledIcons.all { it.closest("p, li") != null })
        assertTrue(sampledIcons.all { it.attr("width").toInt() <= 48 && it.attr("height").toInt() <= 48 })

        val script = asset("web/mobile_article_polish.js")
        val css = asset("styles/fixes.css")
        assertTrue(script.contains("image.closest('p, li, td, th, figcaption')"))
        assertTrue(script.contains("osrsWrapperIsIconChrome"))
        assertTrue(script.contains("osrs-inline-icon-prose"))
        assertTrue(script.contains("p:has(> .osrs-inline-icon-prose)"))
        assertFalse(script.contains("[style*=\"padding\"]"))
        assertTrue(script.contains("paragraph.classList.add('osrs-inline-lore-paragraph')"))
        assertTrue(css.contains(".osrs-inline-lore-note"))
        assertTrue(css.contains(".osrs-inline-icon-prose"))
        assertTrue(css.contains("display: inline !important"))
        assertTrue(css.contains("padding: 0 !important"))
        assertTrue(css.contains("padding-block: 0 !important"))
        assertTrue(css.contains(".osrs-inline-icon-only-paragraph"))
    }

    @Test
    fun recipeDirectSemanticTablesPreserveOrderAndBecomeSeparateRoleDisclosures() {
        val document = fixture("recipe-semantic-variants.html")
        val recipe = document.selectFirst("#semanticRecipe")!!
        val authoredTables = recipe.children().filter {
            it.tagName() == "table" && !it.hasAttr("role") && !it.hasClass("navbox")
        }
        assertEquals(5, authoredTables.size)
        assertEquals(
            listOf("Materials", "", "By-products", "", ""),
            authoredTables.map { it.selectFirst("caption")?.text().orEmpty() }
        )
        val excludedRecipe = document.selectFirst("#nonSemanticRecipeChildren")!!
        val directSemanticTables = excludedRecipe.children().filter {
            it.tagName() == "table" && !it.hasAttr("role") && !it.hasClass("navbox")
        }
        assertEquals(0, directSemanticTables.size)

        val script = asset("web/collapsible_content.js")
        val css = asset("styles/android-article-aesthetics.css")

        assertTrue(script.contains("function directRecipeTables"))
        assertTrue(script.contains("table:not([role=\"presentation\"]):not(.navbox)"))
        assertTrue(script.contains("function recipeRoleForTable"))
        assertTrue(script.contains("'recipe-requirements'"))
        assertTrue(script.contains("'recipe-materials'"))
        assertTrue(script.contains("'recipe-other'"))
        assertTrue(script.contains("labelCounts"))
        assertTrue(script.contains("caption.dataset.osrsCaptionHiddenByDisclosure = 'true'"))
        assertTrue(script.contains("elementToWrap: table"))
        assertTrue(css.contains(".recipe-table.osrs-recipe-unit > .collapsible-container.collapsible-recipe-table"))
        assertTrue(css.contains("width: 100% !important"))
        assertTrue(css.contains(".recipe-table.osrs-recipe-unit > .collapsible-recipe-table .collapsible-close-footer"))
        assertTrue(css.contains("display: block !important"))
    }

    @Test
    fun primaryInfoboxCannotKeepALocalScrollMarkerAndNoVisualCueNodeIsCreated() {
        val fixture = fixture("primary-infobox.html")
        assertTrue(fixture.selectFirst(".infobox-switch")!!.hasClass("osrs-local-scroll-surface"))

        val polish = asset("web/mobile_article_polish.js")
        val interceptor = asset("web/horizontal_scroll_interceptor.js")
        val fixes = asset("styles/fixes.css")
        assertTrue(polish.contains("root.querySelectorAll('.collapsible-primary-infobox, table.main-infobox')"))
        assertTrue(polish.contains("function demoteGenericScrollSurfacesWithin"))
        assertTrue(polish.contains("demoteGenericScrollSurfacesWithin(element)"))
        assertTrue(polish.contains("function demoteGenericScrollSurface"))
        assertTrue(polish.contains("'osrs-local-scroll-surface',"))
        assertTrue(interceptor.contains("table.matches('.main-infobox, .osrs-map-table')"))
        assertTrue(interceptor.contains("table.closest('.collapsible-primary-infobox, .collapsible-map-table, .osrs-recipe-unit')"))
        assertFalse(interceptor.contains("ensureScrollCueLayer"))
        assertFalse(interceptor.contains("document.body.appendChild(cueLayer)"))
        assertFalse(interceptor.contains("className = 'osrs-scroll-cue-layer'"))
        assertTrue(fixes.contains(".collapsible-primary-infobox table.main-infobox"))
        assertTrue(fixes.contains("overflow-x: hidden !important"))
    }

    @Test
    fun teleportationUsesOneConsistentLabelAndTogglesAllFourMapPlaceholders() {
        val document = fixture("amulet-of-glory-teleportation.html")
        val table = document.selectFirst("table.teleportation-options")!!
        assertEquals("Teleportation options", table.previousElementSibling()!!.text())
        assertEquals("Destination / Map", table.selectFirst("caption")!!.text())
        assertEquals(4, table.select(".mw-kartographer-map").size)

        val script = asset("web/collapsible_content.js")
        val toggle = script.substringAfter("function toggleCollapsible")
            .substringBefore("function setupCollapsible")
        val captionDerivation = script.substringAfter("function deriveCaptionText")
            .substringBefore("function recipeRoleForTable")

        assertTrue(captionDerivation.contains("kind !== 'infobox' && table && table.querySelector('.mw-kartographer-map')"))
        assertTrue(captionDerivation.contains("'Map table'"))
        assertFalse(captionDerivation.contains("findContextHeading(elementToWrap)"))
        assertTrue(captionDerivation.contains("return defaultTitle"))
        assertTrue(toggle.contains("Array.from(content.querySelectorAll('.mw-kartographer-map'))"))
        assertTrue(toggle.contains("mapPlaceholders.forEach"))
        assertTrue(toggle.contains("bridgeCall('onCollapsibleToggled', mapPlaceholder.dataset.osrsNativeMapId, isOpening)"))
        assertTrue(script.contains("classes.push('collapsible-map-table')"))
    }

    private fun fixture(name: String) = Jsoup.parse(testResource("article-semantic-contract/$name"), "UTF-8")

    private fun asset(path: String): String = listOf(
        File("src/main/assets", path),
        File("app/src/main/assets", path)
    ).firstOrNull(File::exists)?.readText() ?: error("Missing Android asset: $path")

    private fun testResource(path: String): File = listOf(
        File("src/test/resources", path),
        File("app/src/test/resources", path)
    ).firstOrNull(File::exists) ?: error("Missing Android test resource: $path")
}
