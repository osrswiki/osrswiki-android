package com.omiyawaki.osrswiki.page

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FindInPageNavigationControlsTest {

    @Test
    fun noMatchesDisablePreviousAndNextForTouchAndAccessibility() {
        val controls = inflatedControls()

        FindInPageNavigationControls.apply(
            previousButton = controls.previous,
            nextButton = controls.next,
            hasMatches = false
        )

        listOf(controls.previous, controls.next).forEach { control ->
            assertFalse(control.isEnabled)
            assertFalse(control.isClickable)
            assertFalse(control.isFocusable)
            assertEquals(0.38f, control.alpha, 0.001f)
        }
    }

    @Test
    fun matchesEnablePreviousAndNextForTouchAndAccessibility() {
        val controls = inflatedControls()

        FindInPageNavigationControls.apply(
            previousButton = controls.previous,
            nextButton = controls.next,
            hasMatches = true
        )

        listOf(controls.previous, controls.next).forEach { control ->
            assertTrue(control.isEnabled)
            assertTrue(control.isClickable)
            assertTrue(control.isFocusable)
            assertEquals(1f, control.alpha, 0.001f)
        }
    }

    private fun inflatedControls(): Controls {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_OSRSWiki_OSRSDark
        )
        val root = LayoutInflater.from(context).inflate(R.layout.find_in_page_view, null)
        return Controls(
            previous = root.findViewById(R.id.find_in_page_prev),
            next = root.findViewById(R.id.find_in_page_next)
        )
    }

    private data class Controls(
        val previous: View,
        val next: View
    )
}
