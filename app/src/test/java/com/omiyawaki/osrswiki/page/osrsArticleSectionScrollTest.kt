package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsArticleSectionScrollTest {

    @Test
    fun scriptLooksUpHeadingWithoutThrowingAndScrollsTheDocument() {
        val script = osrsArticleSectionScroll.javaScript("1st_floor2nd_floor")

        assertTrue(script.contains("\"1st_floor2nd_floor\""))
        assertTrue(script.contains("getElementById"))
        assertTrue(script.contains("CSS.escape"))
        assertTrue(script.contains("floornumber-setting-us"))
        assertTrue(script.contains("getBoundingClientRect().top"))
        assertFalse(script.contains("scrollIntoView"))
        assertFalse(script.contains("behavior: 'instant'"))
        assertFalse(script.contains("document.getElementById('1st_floor2nd_floor').scrollIntoView"))
    }

    @Test
    fun scriptEscapesQuotesInAnchors() {
        val script = osrsArticleSectionScroll.javaScript("Other_Fixes_&_What's_Next")
        assertTrue(script.contains("What's_Next") || script.contains("What\\'s_Next") || script.contains("What\\u0027s_Next"))
        assertTrue(script.contains("getElementById"))
    }
}
