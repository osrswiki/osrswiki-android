package com.omiyawaki.osrswiki.readinglist.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SavedPagesSearchEmptyStateTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun blankQueryPromptsSavedPageSearch() {
        assertEquals(
            "Search saved pages",
            SavedPagesSearchEmptyStateText.messageFor(context, query = "", resultCount = 0)
        )
    }

    @Test
    fun queryWithNoResultsNamesSavedPagesAndTheQuery() {
        assertEquals(
            "No saved pages found for \"zulrah\".",
            SavedPagesSearchEmptyStateText.messageFor(context, query = " zulrah ", resultCount = 0)
        )
    }

    @Test
    fun queryWithResultsDoesNotShowEmptyCopy() {
        assertNull(SavedPagesSearchEmptyStateText.messageFor(context, query = "zulrah", resultCount = 1))
    }
}
