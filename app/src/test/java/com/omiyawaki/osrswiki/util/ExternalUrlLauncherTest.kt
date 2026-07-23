package com.omiyawaki.osrswiki.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExternalUrlLauncherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun openStartsActionViewIntentForUrl() {
        var startedIntent: Intent? = null

        val opened = ExternalUrlLauncher.open(
            context = context,
            url = "https://oldschool.runescape.wiki/",
            failureMessage = "Unable to open link",
            startActivity = { intent -> startedIntent = intent }
        )

        assertTrue(opened)
        assertEquals(Intent.ACTION_VIEW, startedIntent?.action)
        assertEquals(Uri.parse("https://oldschool.runescape.wiki/"), startedIntent?.data)
    }

    @Test
    fun openShowsFailureToastWhenNoActivityHandlesIntent() {
        val opened = ExternalUrlLauncher.open(
            context = context,
            url = "https://oldschool.runescape.wiki/",
            failureMessage = "Unable to open link",
            startActivity = { throw ActivityNotFoundException("No handler") }
        )

        assertFalse(opened)
        assertEquals("Unable to open link", ShadowToast.getTextOfLatestToast())
    }
}
