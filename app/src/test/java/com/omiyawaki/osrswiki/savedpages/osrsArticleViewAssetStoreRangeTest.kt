package com.omiyawaki.osrswiki.savedpages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Chromium only treats media as seekable when the response honors byte ranges.
 * The session store must answer Range requests with correct inclusive bounds,
 * otherwise in-article audio seeks snap back to 0.
 */
class osrsArticleViewAssetStoreRangeTest {

    @Test
    fun openEndedRangeCoversTheRestOfTheBody() {
        assertEquals(0 to 999, osrsArticleViewAssetStore.parseByteRange("bytes=0-", 1000))
        assertEquals(500 to 999, osrsArticleViewAssetStore.parseByteRange("bytes=500-", 1000))
    }

    @Test
    fun boundedRangeIsInclusiveAndClampedToTotal() {
        assertEquals(0 to 99, osrsArticleViewAssetStore.parseByteRange("bytes=0-99", 1000))
        assertEquals(900 to 999, osrsArticleViewAssetStore.parseByteRange("bytes=900-5000", 1000))
    }

    @Test
    fun suffixRangeReturnsTheLastBytes() {
        assertEquals(900 to 999, osrsArticleViewAssetStore.parseByteRange("bytes=-100", 1000))
        assertEquals(0 to 999, osrsArticleViewAssetStore.parseByteRange("bytes=-5000", 1000))
    }

    @Test
    fun unsatisfiableOrMalformedRangesAreRejected() {
        assertNull(osrsArticleViewAssetStore.parseByteRange("bytes=1000-", 1000))
        assertNull(osrsArticleViewAssetStore.parseByteRange("bytes=200-100", 1000))
        assertNull(osrsArticleViewAssetStore.parseByteRange("bytes=-", 1000))
        assertNull(osrsArticleViewAssetStore.parseByteRange("bytes=-0", 1000))
        assertNull(osrsArticleViewAssetStore.parseByteRange("items=0-1", 1000))
        assertNull(osrsArticleViewAssetStore.parseByteRange(null, 1000))
        assertNull(osrsArticleViewAssetStore.parseByteRange("bytes=0-", 0))
    }
}
