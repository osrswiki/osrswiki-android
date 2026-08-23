package com.omiyawaki.osrswiki

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityDeepLinkTitleTest {
    private fun titleFromDeepLink(uriString: String): String? {
        val data = Uri.parse(uriString)
        val raw = data.pathSegments
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("/")
            ?: data.lastPathSegment
        return raw?.let { Uri.decode(it).replace("_", " ") }
    }

    @Test
    fun calculatorSubpageKeepsFullPath() {
        assertEquals(
            "Calculator:Agility/Agility arena tickets",
            titleFromDeepLink("osrswiki://page/Calculator:Agility/Agility_arena_tickets")
        )
    }

    @Test
    fun calculatorSingleSegmentStillWorks() {
        assertEquals(
            "Calculator:Efficiency method comparison",
            titleFromDeepLink("osrswiki://page/Calculator:Efficiency_method_comparison")
        )
    }
}
