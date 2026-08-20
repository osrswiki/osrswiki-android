package com.omiyawaki.osrswiki.savedpages

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class osrsArticleViewAssetStoreTest {
    @Before
    fun setUp() {
        osrsArticleViewAssetStore.install(ApplicationProvider.getApplicationContext())
        osrsArticleViewAssetStore.clear()
    }

    @After
    fun tearDown() {
        osrsArticleViewAssetStore.clear()
    }

    @Test
    fun canonicalizeRewritesLocalWikiImagesAndDropsBundledAssets() {
        val wiki = osrsArticleViewAssetStore.canonicalize(
            "https://appassets.androidplatform.net/images/Varrock.png#unused"
        )
        assertEquals("https://oldschool.runescape.wiki/images/Varrock.png", wiki)
        assertNull(
            osrsArticleViewAssetStore.canonicalize(
                "https://appassets.androidplatform.net/styles/themes.css"
            )
        )
    }

    @Test
    fun eligibilityIsWikiArtworkOnly() {
        assertTrue(
            osrsArticleViewAssetStore.isEligible(
                "https://oldschool.runescape.wiki/images/Varrock.png"
            )
        )
        assertFalse(
            osrsArticleViewAssetStore.isEligible(
                "https://oldschool.runescape.wiki/api.php?action=parse"
            )
        )
        assertFalse(
            osrsArticleViewAssetStore.isEligible("https://example.com/images/other.png")
        )
    }

    @Test
    fun putThenGetRoundTripsExactUrlBytes() {
        val url = "https://oldschool.runescape.wiki/images/session.png"
        val bytes = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 9)
        osrsArticleViewAssetStore.put(url, bytes, "image/png")
        val stored = osrsArticleViewAssetStore.get(url)
        assertNotNull(stored)
        assertEquals("image/png", stored!!.contentType)
        assertEquals(bytes.toList(), stored.body.toList())
    }
}
