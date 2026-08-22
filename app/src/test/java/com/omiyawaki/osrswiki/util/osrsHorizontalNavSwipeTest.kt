package com.omiyawaki.osrswiki.util

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Test

class osrsHorizontalNavSwipeTest {
    @Test
    fun ltrSwipeRightIsStartBack() {
        assertEquals(Gravity.START, osrsArticleSwipeGravity(dx = 80f, rtl = false))
    }

    @Test
    fun ltrSwipeLeftIsEndContents() {
        assertEquals(Gravity.END, osrsArticleSwipeGravity(dx = -80f, rtl = false))
    }

    @Test
    fun rtlSwipeRightIsEndContents() {
        assertEquals(Gravity.END, osrsArticleSwipeGravity(dx = 80f, rtl = true))
    }

    @Test
    fun rtlSwipeLeftIsStartBack() {
        assertEquals(Gravity.START, osrsArticleSwipeGravity(dx = -80f, rtl = true))
    }

    @Test
    fun backChromeFollowsTheFinger() {
        assertEquals(540f, osrsBackSwipeTranslationX(progress = 0.5f, width = 1080f, rtl = false), 0.01f)
        assertEquals(-540f, osrsBackSwipeTranslationX(progress = 0.5f, width = 1080f, rtl = true), 0.01f)
        assertEquals(1080f, osrsBackSwipeTranslationX(progress = 1f, width = 1080f, rtl = false), 0.01f)
        assertEquals(-1080f, osrsBackSwipeTranslationX(progress = 1f, width = 1080f, rtl = true), 0.01f)
    }
}
