package com.omiyawaki.osrswiki.history

import android.view.View
import android.widget.ImageButton
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HistoryClearAllButtonStateTest {

    @Test
    fun emptyHistoryHidesAndDisablesClearAll() {
        val button = ImageButton(ApplicationProvider.getApplicationContext())

        HistoryClearAllButtonState.apply(button, hasEntries = false)

        assertEquals(View.GONE, button.visibility)
        assertFalse(button.isEnabled)
        assertFalse(button.isClickable)
        assertFalse(button.isFocusable)
    }

    @Test
    fun historyWithEntriesShowsAndEnablesClearAll() {
        val button = ImageButton(ApplicationProvider.getApplicationContext())

        HistoryClearAllButtonState.apply(button, hasEntries = true)

        assertEquals(View.VISIBLE, button.visibility)
        assertTrue(button.isEnabled)
        assertTrue(button.isClickable)
        assertTrue(button.isFocusable)
    }
}
