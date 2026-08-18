package com.omiyawaki.osrswiki.activity

import androidx.core.graphics.Insets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 36])
class EdgeToEdgeInsetCoordinatorTest {

    @Test
    fun resizePolicyAddsAuthoredAndSystemBarPaddingWithoutAddingImeTwice() {
        val resolved = EdgeToEdgeInsetCoordinator.resolvePadding(
            base = padding(left = 4, top = 8, right = 12, bottom = 16),
            systemBars = padding(left = 5, top = 24, right = 7, bottom = 30),
            statusBars = padding(top = 24),
            navigationBars = padding(left = 5, right = 7, bottom = 30),
            ime = padding(bottom = 400),
            policy = EdgeToEdgeInsetCoordinator.Policy(
                imeInsetHandling = EdgeToEdgeInsetCoordinator.ImeInsetHandling.RESIZE
            )
        )

        assertEquals(padding(left = 9, top = 32, right = 19, bottom = 46), resolved)
    }

    @Test
    fun paddingPolicyUsesLargerOfNavigationAndImeBottomInsets() {
        val base = padding(left = 4, top = 8, right = 12, bottom = 16)
        val policy = EdgeToEdgeInsetCoordinator.Policy(
            imeInsetHandling = EdgeToEdgeInsetCoordinator.ImeInsetHandling.PADDING
        )

        val resolved = EdgeToEdgeInsetCoordinator.resolvePadding(
            base = base,
            systemBars = padding(left = 5, top = 24, right = 7, bottom = 30),
            statusBars = padding(top = 24),
            navigationBars = padding(left = 5, right = 7, bottom = 30),
            ime = padding(bottom = 400),
            policy = policy
        )

        assertEquals(padding(left = 9, top = 32, right = 19, bottom = 416), resolved)
    }

    @Test
    fun displayCutoutWinsPerEdgeWhenLargerThanApi34OrApi36SystemBars() {
        val resolved = EdgeToEdgeInsetCoordinator.resolvePadding(
            base = padding(left = 2, top = 3, right = 4, bottom = 5),
            systemBars = padding(left = 8, top = 24, right = 9, bottom = 30),
            statusBars = padding(top = 24),
            navigationBars = padding(left = 8, right = 9, bottom = 30),
            ime = padding(),
            policy = EdgeToEdgeInsetCoordinator.Policy(),
            displayCutout = padding(left = 42, top = 32, right = 17, bottom = 36)
        )

        assertEquals(padding(left = 44, top = 35, right = 21, bottom = 41), resolved)
    }

    @Test
    fun mainAndArticleCustomBottomChromeUseCutoutAwareSharedResolution() {
        assertEquals(
            Insets.of(42, 24, 19, 36),
            EdgeToEdgeInsetCoordinator.maxPerEdge(
                Insets.of(8, 24, 9, 30),
                Insets.of(42, 0, 19, 36)
            )
        )
        val main = source("MainActivity.kt")
        val page = source("page/PageActivity.kt")
        listOf(main, page).forEach { activity ->
            assertTrue(activity.contains("WindowInsetsCompat.Type.displayCutout()"))
            assertTrue(activity.contains("EdgeToEdgeInsetCoordinator.maxPerEdge"))
        }
    }

    @Test
    fun repeatedResolutionAlwaysStartsFromAuthoredPadding() {
        val base = padding(left = 1, top = 2, right = 3, bottom = 4)
        val systemBars = padding(left = 0, top = 24, right = 0, bottom = 30)
        val policy = EdgeToEdgeInsetCoordinator.Policy()

        val first = EdgeToEdgeInsetCoordinator.resolvePadding(
            base,
            systemBars,
            padding(top = 24),
            padding(bottom = 30),
            padding(),
            policy
        )
        val repeated = EdgeToEdgeInsetCoordinator.resolvePadding(
            base,
            systemBars,
            padding(top = 24),
            padding(bottom = 30),
            padding(),
            policy
        )

        assertEquals(padding(left = 1, top = 26, right = 3, bottom = 34), first)
        assertEquals(first, repeated)
    }

    @Test
    fun savedSearchAppearanceAndGeneralSearchUseTheSharedCoordinator() {
        val baseActivity = source("activity/BaseActivity.kt")
        val savedSearch = source("readinglist/ui/SavedPagesSearchActivity.kt")
        val appearance = source("settings/AppearanceSettingsActivity.kt")
        val search = source("search/SearchActivity.kt")

        assertTrue(baseActivity.contains("EdgeToEdgeInsetCoordinator.apply"))
        assertTrue(savedSearch.contains("applyEdgeToEdgeInsets(binding.root)"))
        assertTrue(appearance.contains("applyEdgeToEdgeInsets(findViewById(android.R.id.content))"))
        assertTrue(search.contains("applyEdgeToEdgeInsets(binding.root)"))
        assertFalse(savedSearch.contains("setOnApplyWindowInsetsListener"))
        assertFalse(appearance.contains("setOnApplyWindowInsetsListener"))
        assertFalse(search.contains("setOnApplyWindowInsetsListener"))
    }

    @Test
    fun informationalSubpagesShareInsetsWhileFeedbackFormsRetainTheirBottomHandler() {
        listOf(
            "about/AboutActivity.kt",
            "about/PrivacyPolicyActivity.kt",
            "donate/DonateActivity.kt",
            "feedback/FeedbackActivity.kt"
        ).forEach { path ->
            assertTrue(source(path).contains("applyEdgeToEdgeInsets(findViewById(android.R.id.content))"))
        }

        listOf(
            "feedback/ReportIssueActivity.kt",
            "feedback/RequestFeatureActivity.kt"
        ).forEach { path ->
            val source = source(path)
            assertTrue(source.contains("applyEdgeToEdgeInsets(binding.root, applyNavigationBar = false)"))
            assertTrue(source.contains("WindowInsetsCompat.Type.navigationBars()"))
        }
    }

    private fun padding(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ) = EdgeToEdgeInsetCoordinator.Padding(left, top, right, bottom)

    private fun source(relativePath: String): String =
        File("src/main/java/com/omiyawaki/osrswiki/$relativePath").readText()
}
