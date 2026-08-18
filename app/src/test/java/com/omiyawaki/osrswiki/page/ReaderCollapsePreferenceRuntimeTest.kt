package com.omiyawaki.osrswiki.page

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCollapsePreferenceRuntimeTest {
    @Test
    fun returnFromAppearanceUpdatesExistingDisclosuresWithoutReloadingDocument() {
        val script = PageHtmlBuilder.tableCollapseRuntimeScript(collapseTablesEnabled = false)

        assertTrue(script.contains("window.OSRS_TABLE_COLLAPSED = shouldCollapse"))
        assertTrue(script.contains("document.querySelectorAll('.collapsible-container')"))
        assertTrue(script.contains("container.classList.contains('primary-collapsible')"))
        assertTrue(script.contains("header.click()"))
        assertFalse(script.contains("location.reload"))
        assertEquals(1, Regex("\\(function\\(\\) \\{").findAll(script).count())
        assertEquals(1, Regex("\\}\\)\\(\\);").findAll(script).count())

        val fragmentSource = source("page/PageFragment.kt")
        val managerSource = source("page/PageWebViewManager.kt")
        assertTrue(fragmentSource.contains("collapsePreference != lastAppliedCollapsePreference"))
        assertTrue(fragmentSource.contains("refreshTableCollapsePreference()"))
        assertTrue(managerSource.contains("applyTableCollapsePreference {"))
        assertTrue(managerSource.contains("PageHtmlBuilder.tableCollapseRuntimeScript"))
    }

    @Test
    fun packagedDisclosureHandlerKeepsCollapsedContentInertAndReturnsFocus() {
        val script = asset("web/collapsible_content.js")

        assertTrue(script.contains("setAttribute('aria-hidden', 'true')"))
        assertTrue(script.contains("content.inert = true"))
        assertTrue(script.contains("header.focus"))
        assertTrue(script.contains("setAttribute('aria-expanded'"))
    }

    private fun source(path: String): String = listOf(
        File("src/main/java/com/omiyawaki/osrswiki", path),
        File("app/src/main/java/com/omiyawaki/osrswiki", path)
    ).firstOrNull(File::exists)?.readText() ?: error("Missing source: $path")

    private fun asset(path: String): String = listOf(
        File("src/main/assets", path),
        File("app/src/main/assets", path)
    ).firstOrNull(File::exists)?.readText() ?: error("Missing asset: $path")
}
