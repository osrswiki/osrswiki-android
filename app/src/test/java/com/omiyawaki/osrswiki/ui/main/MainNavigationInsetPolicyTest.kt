package com.omiyawaki.osrswiki.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationInsetPolicyTest {

    @Test
    fun hostBottomMarginMatchesTranslatedBottomNavigationInset() {
        assertEquals(0, MainNavigationInsetPolicy.hostBottomMarginForNavigationInset(0))
        assertEquals(63, MainNavigationInsetPolicy.hostBottomMarginForNavigationInset(63))
    }

    @Test
    fun hostBottomMarginClampsNegativeInsets() {
        assertEquals(0, MainNavigationInsetPolicy.hostBottomMarginForNavigationInset(-1))
    }
}
