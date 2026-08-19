package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppWebViewClientEmbedPolicyTest {
    @Test
    fun osrsRecognizesYouTubeEmbedHosts() {
        assertTrue(AppWebViewClient.osrsIsExternalMediaHost("www.youtube.com"))
        assertTrue(AppWebViewClient.osrsIsExternalMediaHost("youtube.com"))
        assertTrue(AppWebViewClient.osrsIsExternalMediaHost("www.youtube-nocookie.com"))
        assertTrue(AppWebViewClient.osrsIsExternalMediaHost("youtu.be"))
        assertFalse(AppWebViewClient.osrsIsExternalMediaHost("oldschool.runescape.wiki"))
        assertFalse(AppWebViewClient.osrsIsExternalMediaHost(null))
    }

    @Test
    fun shouldOverrideUrlLoadingAllowsSubframesBeforeOpeningExternalApps() {
        val source = File("src/main/java/com/omiyawaki/osrswiki/page/AppWebViewClient.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/omiyawaki/osrswiki/page/AppWebViewClient.kt")
        }.readText()
        assertTrue(source.contains("osrsShouldOverrideMainFrameNavigation"))
        assertTrue(source.contains("osrsShouldOpenExternalUriWithoutUserGesture"))
        assertTrue(source.contains("if (!osrsShouldOverrideMainFrameNavigation(request))"))
        assertTrue(source.contains("return false"))
    }
}
