package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.settings.ReaderPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReaderGesturePolicyTest {

    @Test
    fun allFourPreferenceCombinationsGateBackAndContentsIndependently() {
        assertPolicy(rightBack = false, leftContents = false, back = false, contents = false)
        assertPolicy(rightBack = true, leftContents = false, back = true, contents = false)
        assertPolicy(rightBack = false, leftContents = true, back = false, contents = true)
        assertPolicy(rightBack = true, leftContents = true, back = true, contents = true)
    }

    @Test
    fun policyReadsTheLatestPreferencesWithoutRecreatingTheArticle() {
        var current = ReaderPreferences(
            swipeRightBackEnabled = true,
            swipeLeftContentsEnabled = false
        )
        fun enabled(action: ReaderSwipeAction) = ReaderGesturePolicy.isEnabled(action, current)

        assertTrue(enabled(ReaderSwipeAction.BACK))
        assertFalse(enabled(ReaderSwipeAction.CONTENTS))

        current = current.copy(
            swipeRightBackEnabled = false,
            swipeLeftContentsEnabled = true
        )

        assertFalse(enabled(ReaderSwipeAction.BACK))
        assertTrue(enabled(ReaderSwipeAction.CONTENTS))
    }

    @Test
    fun preferenceGateRunsOnlyAfterExistingOwnershipDecision() {
        val source = File("src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/omiyawaki/osrswiki/page/PageFragment.kt")
        }.readText()
        val onFling = source.substringAfter("override fun onFling(")
            .substringBefore("gestureDetector = GestureDetector")
        val asynchronousOwnership = source.substringAfter("private fun resolveArticleSwipeOwnership")
            .substringBefore("private fun dispatchPageSwipeIfEnabled")
        val dispatch = source.substringAfter("private fun dispatchPageSwipeIfEnabled")
            .substringBefore("internal fun onArticleDomTouchSequence")

        assertTrue(onFling.indexOf("horizontalGestureOwnership.owns(generation)") < onFling.indexOf("registerNavigationCandidate(generation)"))
        assertTrue(onFling.indexOf("registerNavigationCandidate(generation)") < onFling.indexOf("dispatchPageSwipeIfEnabled(gravity)"))
        assertTrue(asynchronousOwnership.contains("recordFinalClassification(generation, snapshot)"))
        assertTrue(asynchronousOwnership.contains("dispatchPageSwipeIfEnabled(gravity)"))
        assertTrue(dispatch.contains("Prefs.readerPreferences"))
        assertTrue(dispatch.contains("ReaderGesturePolicy.isEnabled"))
    }

    @Test
    fun activityHasDefenseInDepthBeforeBackOrContentsDispatch() {
        val source = File("src/main/java/com/omiyawaki/osrswiki/page/PageActivity.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/omiyawaki/osrswiki/page/PageActivity.kt")
        }.readText()
        val swipeHandler = source.substringAfter("override fun onPageSwipe(gravity: Int, velocityX: Float)")
            .substringBefore("fun showContents()")

        assertTrue(swipeHandler.indexOf("ReaderGesturePolicy.isEnabled") < swipeHandler.indexOf("openDrawer"))
        assertTrue(swipeHandler.indexOf("ReaderGesturePolicy.isEnabled") < swipeHandler.indexOf("onBackPressedDispatcher.onBackPressed()"))
    }

    private fun assertPolicy(
        rightBack: Boolean,
        leftContents: Boolean,
        back: Boolean,
        contents: Boolean
    ) {
        val preferences = ReaderPreferences(
            swipeRightBackEnabled = rightBack,
            swipeLeftContentsEnabled = leftContents
        )
        if (back) {
            assertTrue(ReaderGesturePolicy.isEnabled(ReaderSwipeAction.BACK, preferences))
        } else {
            assertFalse(ReaderGesturePolicy.isEnabled(ReaderSwipeAction.BACK, preferences))
        }
        if (contents) {
            assertTrue(ReaderGesturePolicy.isEnabled(ReaderSwipeAction.CONTENTS, preferences))
        } else {
            assertFalse(ReaderGesturePolicy.isEnabled(ReaderSwipeAction.CONTENTS, preferences))
        }
    }
}
