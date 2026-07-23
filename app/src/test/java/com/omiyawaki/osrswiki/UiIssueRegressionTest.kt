package com.omiyawaki.osrswiki

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.databinding.FragmentSearchResultsBinding
import com.omiyawaki.osrswiki.page.AppWebViewClient
import com.omiyawaki.osrswiki.ui.map.osrsMapDefaultView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UiIssueRegressionTest {

    private fun themedContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return ContextThemeWrapper(context, R.style.Theme_OSRSWiki_OSRSDark)
    }

    @Test
    fun pageToolbarSearchHintUsesSpacedWikiName() {
        val context = themedContext()

        assertEquals("Search OSRS Wiki", context.getString(R.string.page_toolbar_search_hint))
    }

    @Test
    fun searchActivityClearButtonHasClearSearchContentDescription() {
        val layout = sourceFile("res/layout/activity_search.xml").readText()
        val clearSearchButtonBlock = Regex(
            """<ImageView\s+android:id="@\+id/clear_search_button"[\s\S]*?/>"""
        ).find(layout)?.value.orEmpty()

        assertTrue(
            clearSearchButtonBlock.contains(
                """android:contentDescription="@string/search_clear_content_description""""
            )
        )
    }

    @Test
    fun searchOfflineIndicatorHasRuntimeText() {
        val context = themedContext()
        val binding = FragmentSearchResultsBinding.inflate(LayoutInflater.from(context))

        assertEquals(
            context.getString(R.string.offline_search_active_message),
            binding.textViewOfflineIndicator.text.toString()
        )
    }

    @Test
    fun clipboardBridgeDoesNotWriteTestValueOnWindowLoad() {
        val sharedBridge = repoFile("../../shared/js/clipboard_bridge.js")
        val androidBridge = repoFile("app/src/main/assets/web/clipboard_bridge.js")

        listOf(sharedBridge, androidBridge).forEach { bridgeFile ->
            val bridgeSource = bridgeFile.readText()
            assertFalse(
                "${bridgeFile.path} must not write a test value to the clipboard",
                bridgeSource.contains("writeText('test')") ||
                    bridgeSource.contains("writeText(\"test\")")
            )
        }
    }

    @Test
    fun appassetsLoadPhpModuleUrlNormalizesToWikiHost() {
        val localModuleUrl = "https://appassets.androidplatform.net/load.php?modules=jquery&only=scripts"

        assertEquals(
            "https://oldschool.runescape.wiki/load.php?modules=jquery&only=scripts",
            AppWebViewClient.normalizeModuleCacheUrl(localModuleUrl)
        )
    }

    @Test
    fun moreLaunchedActivitiesDeclareToolbarNavigation() {
        listOf(
            "res/layout/activity_appearance_settings.xml",
            "res/layout/activity_donate.xml",
            "res/layout/activity_about.xml",
            "res/layout/activity_feedback.xml",
            "res/layout/activity_privacy_policy.xml"
        ).forEach { layoutPath ->
            val layout = sourceFile(layoutPath).readText()
            assertTrue("$layoutPath must declare a MaterialToolbar", layout.contains("MaterialToolbar"))
            assertTrue(
                "$layoutPath must expose an up affordance",
                layout.contains("""app:navigationIcon="@drawable/ic_arrow_back"""")
            )
        }
    }

    @Test
    fun feedbackFormsHaveInsetAwareScrollContentAndNoStaticToolbarMenu() {
        listOf(
            "res/layout/activity_report_issue.xml",
            "res/layout/activity_request_feature.xml"
        ).forEach { layoutPath ->
            val layout = sourceFile(layoutPath).readText()
            assertTrue(layout.contains("""android:id="@+id/content_scroll_view""""))
            assertTrue(layout.contains("""android:id="@+id/form_content_container""""))
            assertTrue(layout.contains("""android:clipToPadding="false""""))
            assertFalse(layout.contains("""app:menu="@menu/menu_submit""""))
        }
    }

    @Test
    fun recentSearchesClearActionUsesSurfaceTextColor() {
        val layout = sourceFile("res/layout/fragment_recent_searches.xml").readText()

        assertTrue(layout.contains("""android:textColor="?attr/colorOnSurface""""))
    }

    @Test
    fun savedSwipeDeleteShowsUndoBeforeDeleting() {
        val source = sourceFile("java/com/omiyawaki/osrswiki/readinglist/ui/SavedPagesFragment.kt").readText()

        assertTrue(source.contains("Snackbar.make"))
        assertTrue(source.contains("R.string.saved_page_delete_pending"))
        assertTrue(source.contains("R.string.action_undo"))
        assertTrue(source.contains("Snackbar.Callback()"))
        assertTrue(source.contains("viewModel.deleteSavedPage(savedPage)"))
        assertTrue(source.indexOf("Snackbar.make") < source.indexOf("viewModel.deleteSavedPage(savedPage)"))
    }

    @Test
    fun mapDefaultCenterUsesLumbridgeTownCoordinate() {
        val expectedLatitude = "-25.44327461230575"
        val expectedLongitude = "-130.2978515625"

        assertEquals(expectedLatitude.toDouble(), osrsMapDefaultView.LATITUDE, 0.000000000001)
        assertEquals(expectedLongitude.toDouble(), osrsMapDefaultView.LONGITUDE, 0.000000000001)
        assertEquals(3222.0, osrsMapDefaultView.GAME_X, 0.0)
        assertEquals(3218.0, osrsMapDefaultView.GAME_Y, 0.0)
        assertEquals(960.0, osrsMapDefaultView.GAME_MIN_X, 0.0)
        assertEquals(4224.0, osrsMapDefaultView.GAME_MAX_X, 0.0)
        assertEquals(13056.0, osrsMapDefaultView.SOURCE_IMAGE_WIDTH, 0.0)

        listOf(
            "java/com/omiyawaki/osrswiki/ui/map/MapFragment.kt",
            "java/com/omiyawaki/osrswiki/ui/map/AndroidMapPreloader.kt"
        ).forEach { sourcePath ->
            val source = sourceFile(sourcePath).readText()
            assertTrue("$sourcePath should use the generated map default view", source.contains("osrsMapDefaultView"))
            assertFalse("$sourcePath must not hardcode the stale western map extent", source.contains("12800.0 / 4.0"))
            assertFalse("$sourcePath must not hardcode the stale map origin", source.contains("val gameMinX = 1024.0"))
        }
    }

    @Test
    fun mapAssetSyncScriptStampsDefaultViewSources() {
        val script = repoFile("scripts/shared/sync-mbtiles-to-platforms.sh").readText()

        assertTrue(script.contains("stamp_default_map_view"))
        assertTrue(script.contains("shared/map-default-view.json"))
        assertTrue(script.contains("stamp-map-default-view.py"))
        assertTrue(script.contains("map-metadata.json"))
        assertTrue(script.contains("osrsMapDefaultView.swift"))
        assertTrue(script.contains("osrsMapDefaultView.kt"))
    }

    @Test
    fun androidMapAssetsIncludeMbtilesForEveryFloor() {
        (0..3).forEach { floor ->
            val asset = repoFile("platforms/android/app/src/main/assets/map_floor_$floor.mbtiles")
            assertTrue("Android map floor $floor MBTiles asset must exist", asset.exists())
            assertTrue("Android map floor $floor MBTiles asset must not be empty", asset.length() > 0)
        }
    }

    @Test
    fun androidManifestDisablesAppWideCleartextTraffic() {
        val manifest = sourceFile("AndroidManifest.xml").readText()

        assertTrue(manifest.contains("""android:usesCleartextTraffic="false""""))
        assertFalse(manifest.contains("""android:usesCleartextTraffic="true""""))
    }

    @Test
    fun releaseBuildPolicyFailsOnLintAndMissingMapAssets() {
        val gradle = repoFile("platforms/android/app/build.gradle.kts").readText()

        assertTrue(gradle.contains("checkReleaseBuilds = true"))
        assertTrue(gradle.contains("abortOnError = true"))
        assertTrue(gradle.contains("validateReleaseAssets"))
        assertTrue(gradle.contains("requiredReleaseMapAssets"))
        assertTrue(gradle.contains("map-metadata.json"))
        (0..3).forEach { floor ->
            assertTrue(gradle.contains("map_floor_$floor.mbtiles"))
        }
    }

    @Test
    fun mainNavigationHostTracksTranslatedBottomNavInset() {
        val source = sourceFile("java/com/omiyawaki/osrswiki/MainActivity.kt").readText()

        assertTrue(source.contains("MainNavigationInsetPolicy.hostBottomMarginForNavigationInset"))
        assertTrue(source.contains("layoutParams.bottomMargin"))
    }

    @Test
    fun mainNavigationRestoresFragmentsSafelyAndSkipsTransactionsAfterStateSave() {
        val source = sourceFile("java/com/omiyawaki/osrswiki/MainActivity.kt").readText()

        assertTrue(source.contains("restoreFragment("))
        assertFalse(source.contains(" as MainFragment"))
        assertFalse(source.contains(" as StandardNavigationMapFragment"))
        assertFalse(source.contains(" as HistoryFragment"))
        assertFalse(source.contains(" as SavedPagesFragment"))
        assertFalse(source.contains(" as MoreFragment"))
        assertTrue(source.contains("supportFragmentManager.isStateSaved"))
        assertTrue(source.contains("Skipping fragment visibility refresh"))
    }

    @Test
    fun searchPagingCollectionDoesNotRefreshAdapterInsideSubmitPath() {
        val source = sourceFile("java/com/omiyawaki/osrswiki/search/SearchResultsFragment.kt").readText()

        assertFalse(source.contains("onlineSearchAdapter.refresh()"))
    }

    @Test
    fun debugLoggingUtilityDoesNotEmitDebugMessagesAtErrorSeverity() {
        val source = sourceFile("java/com/omiyawaki/osrswiki/util/log/L.kt").readText()

        assertTrue(source.contains("fun d(message: String)"))
        assertTrue(source.contains("Log.d(TAG"))
        assertFalse(source.contains("Log.e(TAG, \"${'$'}{sdf.format(Date())} - DEBUG: ${'$'}message\")"))
    }

    @Test
    fun feedbackProductionSourcesDoNotCompileDirectGitHubSubmissionPath() {
        val directGitHubSources = listOf(
            "java/com/omiyawaki/osrswiki/network/GitHubRetrofitClient.kt",
            "java/com/omiyawaki/osrswiki/network/GitHubApiService.kt",
            "java/com/omiyawaki/osrswiki/feedback/FeedbackRepository.kt",
            "java/com/omiyawaki/osrswiki/feedback/FeedbackFragment.kt"
        )

        directGitHubSources.forEach { sourcePath ->
            assertFalse("$sourcePath must not remain in production sources", optionalSourceFile(sourcePath).exists())
        }

        val sourceRoot = sourceFile("java/com/omiyawaki/osrswiki")
        val githubTokenReferences = sourceRoot.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension in setOf("kt", "java") }
            .filter { it.readText().contains("github_pat_placeholder") }
            .toList()

        assertTrue("No production Kotlin/Java source may contain the placeholder GitHub token", githubTokenReferences.isEmpty())
    }

    @Test
    fun mapFloorControlsUseSharedActionabilityPolicy() {
        listOf(
            "java/com/omiyawaki/osrswiki/ui/map/MapFragment.kt",
            "java/com/omiyawaki/osrswiki/ui/map/StandardNavigationMapFragment.kt"
        ).forEach { sourcePath ->
            val source = sourceFile(sourcePath).readText()
            assertTrue(source.contains("MapFloorControlPolicy.state"))
            assertTrue(source.contains("isClickable = state.isActionable"))
            assertTrue(source.contains("isFocusable = state.isActionable"))
            assertTrue(source.contains("map_accessibility_description"))
        }
    }

    @Test
    fun mapRasterMarkersExposeExplicitNonInteractiveInfoAffordance() {
        val context = themedContext()
        val mapLayout = sourceFile("res/layout/fragment_map.xml").readText()
        val mapStrings = sourceFile("res/values/strings_map.xml").readText()

        assertTrue(mapLayout.contains("""android:id="@+id/map_marker_info_button""""))
        assertTrue(mapLayout.contains("""android:contentDescription="@string/map_marker_info_action""""))
        assertTrue(mapLayout.contains("""android:src="@drawable/ic_info_24""""))
        assertTrue(mapStrings.contains("map_marker_noninteractive_message"))
        assertTrue(
            context.getString(R.string.map_accessibility_description)
                .contains("not individually selectable")
        )

        listOf(
            "java/com/omiyawaki/osrswiki/ui/map/MapFragment.kt",
            "java/com/omiyawaki/osrswiki/ui/map/StandardNavigationMapFragment.kt"
        ).forEach { sourcePath ->
            val source = sourceFile(sourcePath).readText()
            assertTrue(source.contains("setupMarkerInfoControl"))
            assertTrue(source.contains("R.string.map_marker_noninteractive_message"))
            assertTrue(source.contains("Toast.makeText"))
            assertFalse("$sourcePath must not pretend raster markers are vector symbols", source.contains("SymbolLayer"))
        }
    }

    @Test
    fun tabletLayoutsConstrainSecondarySurfacesWithoutChangingMapFullBleed() {
        val dimens = sourceFile("res/values/dimens.xml").readText()
        val moreLayout = sourceFile("res/layout/fragment_more.xml").readText()
        val mapLayout = sourceFile("res/layout/fragment_map.xml").readText()
        val donateTabletLayout = sourceFile("res/layout-sw600dp/fragment_donate.xml").readText()
        val feedbackTabletLayout = sourceFile("res/layout-sw600dp/fragment_feedback.xml").readText()

        assertTrue(dimens.contains("""<dimen name="tablet_content_max_width">720dp</dimen>"""))
        assertTrue(moreLayout.contains("""app:layout_constraintWidth_max="@dimen/tablet_content_max_width""""))
        assertTrue(donateTabletLayout.contains("""android:id="@+id/donate_content_container""""))
        assertTrue(donateTabletLayout.contains("""app:layout_constraintWidth_max="@dimen/tablet_content_max_width""""))
        assertTrue(feedbackTabletLayout.contains("""android:id="@+id/feedback_content_container""""))
        assertTrue(feedbackTabletLayout.contains("""app:layout_constraintWidth_max="@dimen/tablet_content_max_width""""))
        assertTrue(mapLayout.contains("""android:id="@+id/map_view""""))
        assertTrue(mapLayout.contains("""app:layout_constraintBottom_toBottomOf="parent""""))
    }

    private fun sourceFile(path: String): File {
        return listOf(
            File("src/main", path),
            File("app/src/main", path)
        ).first { it.exists() }
    }

    private fun optionalSourceFile(path: String): File {
        return listOf(
            File("src/main", path),
            File("app/src/main", path)
        ).firstOrNull { it.exists() } ?: File("src/main", path)
    }

    private fun repoFile(path: String): File {
        return listOf(
            File(path),
            File("..", path),
            File("../..", path),
            File("../../..", path),
            File("../../../..", path)
        ).firstOrNull { it.exists() }
            ?: error("Could not find repo file: $path")
    }
}
