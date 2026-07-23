package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.content.Context
import android.content.res.Configuration
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.omiyawaki.osrswiki.undergroundmaps.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class osrsRealmIdentityLayoutTest {
    @Test
    fun `longest identity is complete or explicitly ellipsized at supported fonts and widths`() {
        OSRS_FONT_SCALES.forEach { fontScale ->
            OSRS_VIEWPORT_WIDTHS_DP.forEach { viewportWidthDp ->
                val button = identityButton(fontScale, viewportWidthDp)
                val state = button.osrsRealmIdentityLayoutStateOrNull()
                assertNotNull(state)

                assertEquals(OSRS_REALM_IDENTITY_MAX_LINES, button.maxLines)
                assertEquals(TextUtils.TruncateAt.END, button.ellipsize)
                assertTrue(
                    "Identity silently clipped at font=$fontScale width=${viewportWidthDp}dp: $state",
                    state!!.honest
                )
                assertTrue(button.contentDescription.toString().contains(OSRS_LONGEST_IDENTITY))
                assertTrue(
                    button.measuredHeight >=
                        button.compoundPaddingTop + button.layout.height + button.compoundPaddingBottom
                )
            }
        }
    }

    @Test
    fun `wide portrait shows the entire longest identity without truncation`() {
        OSRS_FONT_SCALES.forEach { fontScale ->
            val button = identityButton(fontScale, OSRS_PIXEL_5_CONTENT_WIDTH_DP)
            val state = requireNotNull(button.osrsRealmIdentityLayoutStateOrNull())

            assertEquals(0, state.ellipsisCount)
            assertEquals(OSRS_LONGEST_IDENTITY.length, state.lastVisibleEnd)
            assertFalse(button.isAllCaps)
        }
    }

    private fun identityButton(fontScale: Float, widthDp: Int): MaterialButton {
        val applicationContext: Context = ApplicationProvider.getApplicationContext()
        val configuration = Configuration(applicationContext.resources.configuration).apply {
            this.fontScale = fontScale
        }
        val configuredContext = applicationContext.createConfigurationContext(configuration)
        val themedContext = ContextThemeWrapper(
            configuredContext,
            R.style.Theme_OsrsUndergroundMaps
        )
        return MaterialButton(themedContext).apply {
            text = OSRS_LONGEST_IDENTITY
            contentDescription = "Current map: $OSRS_LONGEST_IDENTITY"
            isAllCaps = false
            osrsApplyRealmIdentityLayout()
            val widthPixels = (widthDp * resources.displayMetrics.density).toInt()
            measure(
                View.MeasureSpec.makeMeasureSpec(widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    private companion object {
        const val OSRS_LONGEST_IDENTITY =
            "Underground Pass - bottom level (Song of the Elves instance)"
        const val OSRS_PIXEL_5_CONTENT_WIDTH_DP = 369
        val OSRS_FONT_SCALES = listOf(1.0f, 1.5f, 2.0f)
        val OSRS_VIEWPORT_WIDTHS_DP = listOf(280, OSRS_PIXEL_5_CONTENT_WIDTH_DP, 640)
    }
}
