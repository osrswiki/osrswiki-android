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
}
