package com.omiyawaki.osrswiki.settings

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ProductionDiagnosticsGuardTest {

    @Test
    fun appStartupDoesNotScheduleAppWidePreviewWebViewWork() {
        val appSource = source("src/main/java/com/omiyawaki/osrswiki/OSRSWikiApp.kt")

        assertFalse(appSource.contains("scheduleBackgroundPreviewGeneration()"))
        assertFalse(appSource.contains("initializeBackgroundGeneration(this@OSRSWikiApp"))
    }

    @Test
    fun mainResumeDoesNotResetOrStartPreviewGeneration() {
        val mainSource = source("src/main/java/com/omiyawaki/osrswiki/MainActivity.kt")

        assertFalse(mainSource.contains("PreviewGenerationManager.resetState()"))
        assertFalse(mainSource.contains("PreviewGenerationManager.initializeBackgroundGeneration"))
    }

    @Test
    fun previewManagerDoesNotLaunchUnjoinedActivityBoundWork() {
        val managerSource = source("src/main/java/com/omiyawaki/osrswiki/settings/PreviewGenerationManager.kt")

        assertFalse(managerSource.contains("app.applicationScope.launch"))
        assertFalse(managerSource.contains("ActivityContextPool.onActivityReady"))
    }

    @Test
    fun appearanceSettingsUseStaticPreviewAssetsInsteadOfDynamicRenderers() {
        val fragmentSource = source("src/main/java/com/omiyawaki/osrswiki/settings/CustomAppearanceSettingsFragment.kt")
        val themeAdapterSource = source("src/main/java/com/omiyawaki/osrswiki/settings/InlineThemeSelectionAdapter.kt")
        val modularTableAdapterSource = source("src/main/java/com/omiyawaki/osrswiki/settings/ModularTableSelectionAdapter.kt")
        val inlineTableAdapterSource = source("src/main/java/com/omiyawaki/osrswiki/settings/InlineTableSelectionAdapter.kt")

        assertFalse(fragmentSource.contains("initializeBackgroundPreviewGeneration()"))
        assertFalse(fragmentSource.contains("preWarmThemePreviewCache()"))
        assertFalse(fragmentSource.contains("preWarmTablePreviewCache()"))
        assertFalse(fragmentSource.contains("PreviewGenerationManager.initializeBackgroundGeneration"))
        assertFalse(themeAdapterSource.contains("ThemePreviewRenderer.getPreview"))
        assertFalse(modularTableAdapterSource.contains("TablePreviewRenderer.getPreview"))
        assertFalse(inlineTableAdapterSource.contains("TablePreviewRenderer.getPreview"))
    }

    @Test
    fun productionDiagnosticProbesAreNotStartedByNormalActivities() {
        val mainSource = source("src/main/java/com/omiyawaki/osrswiki/MainActivity.kt")
        val searchSource = source("src/main/java/com/omiyawaki/osrswiki/search/SearchActivity.kt")
        val searchResultsSource = source("src/main/java/com/omiyawaki/osrswiki/search/SearchResultsFragment.kt")

        assertFalse(mainSource.contains("ColorExtractor.exportColorsToJSON"))
        assertFalse(mainSource.contains("testSearchItemColors()"))
        assertFalse(mainSource.contains("testSearchAdapterDirectly()"))
        assertFalse(searchSource.contains("testColorConsistency()"))
        assertFalse(searchResultsSource.contains("PixelColorTest"))
    }

    private fun source(path: String): String = File(path).readText()
}
