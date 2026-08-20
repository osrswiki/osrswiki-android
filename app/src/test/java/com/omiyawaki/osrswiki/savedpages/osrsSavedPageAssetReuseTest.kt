package com.omiyawaki.osrswiki.savedpages

import org.junit.Assert.assertEquals
import org.junit.Test

class osrsSavedPageAssetReuseTest {
    @Test
    fun partitionsUnchangedUrlsForReuseAndNewUrlsForFetch() {
        val partition = osrsSavedPageAssetReuse.partition(
            requiredUrls = listOf(
                "https://wiki/old.png",
                "https://wiki/new.png",
                "https://wiki/old.png"
            ),
            priorUrls = setOf("https://wiki/old.png", "https://wiki/unused.png")
        )
        assertEquals(listOf("https://wiki/old.png"), partition.reusedUrls)
        assertEquals(listOf("https://wiki/new.png"), partition.fetchUrls)
    }

    @Test
    fun copySourceUrlsUnionPriorAndSessionHits() {
        val combined = osrsSavedPageAssetReuse.copySourceUrls(
            priorUrls = setOf("https://wiki/old.png"),
            sessionUrls = setOf("https://wiki/old.png", "https://wiki/viewed.png")
        )
        assertEquals(
            setOf("https://wiki/old.png", "https://wiki/viewed.png"),
            combined
        )
    }
}
