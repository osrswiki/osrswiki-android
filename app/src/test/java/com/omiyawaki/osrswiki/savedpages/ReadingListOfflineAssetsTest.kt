package com.omiyawaki.osrswiki.savedpages

import com.omiyawaki.osrswiki.offline.db.OfflineObject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingListOfflineAssetsTest {
    @Test
    fun extractorEnumeratesEveryUniqueMediaReferenceWithoutCap() {
        val manyImages = (1..1_101).joinToString("") { index ->
            "<img src='/images/item-$index.png'>"
        }
        val html = """
            $manyImages
            <img src="//oldschool.runescape.wiki/images/animated.gif"
                 srcset="/images/animated-small.gif 1x, /images/animated-large.gif 2x">
            <picture><source src="/images/picture.webp"
                 srcset="/images/picture-small.webp 1x, /images/picture-large.webp 2x"></picture>
            <video src="/ignored/video.mp4" poster="/images/poster.webp">
                <source src="/ignored/video-source.mp4">
            </video>
            <img data-osrs-deferred-src="/images/deferred.png"
                 data-osrs-deferred-srcset="/images/deferred-2x.png 2x">
            <div style="background-image: url('/images/background.jpg')"></div>
            <style>
                @import '/css/article-art.css';
                .hero { background: url('/images/style-block.webp'); }
                /* url('/images/comment-only.png') */
            </style>
            <svg><image href="/images/vector.svg" xlink:href="/images/vector-fallback.svg"></image></svg>
            <object type="image/svg+xml" data="/images/object-art.svg"></object>
            <link rel="stylesheet" href="/css/rendered.css">
            <img src="data:image/png;base64,ignored">
            <img src="/images/item-1.png">
            <a href="/navigation-is-not-an-asset.png">Navigation</a>
            <script src="/ignored/script.js"></script>
            <iframe src="/ignored/frame.html"></iframe>
            <embed src="/ignored/embed.bin">
            <audio src="/ignored/audio.ogg"><source src="/ignored/audio-source.ogg"></audio>
            <track src="/ignored/captions.vtt">
            <source src="/ignored/orphan-source.png">
            <object type="application/pdf" data="/ignored/document.pdf"></object>
        """.trimIndent()

        val urls = ReadingListAssetUrlExtractor.extract(html)

        assertEquals(1_117, urls.size)
        assertTrue("https://oldschool.runescape.wiki/images/animated.gif" in urls)
        assertTrue("https://oldschool.runescape.wiki/images/animated-large.gif" in urls)
        assertTrue("https://oldschool.runescape.wiki/images/picture-large.webp" in urls)
        assertTrue("https://oldschool.runescape.wiki/images/poster.webp" in urls)
        assertTrue("https://oldschool.runescape.wiki/images/background.jpg" in urls)
        assertTrue("https://oldschool.runescape.wiki/images/style-block.webp" in urls)
        assertTrue("https://oldschool.runescape.wiki/css/article-art.css" in urls)
        assertTrue("https://oldschool.runescape.wiki/images/vector.svg" in urls)
        assertTrue("https://oldschool.runescape.wiki/images/object-art.svg" in urls)
        assertFalse(urls.any { it.startsWith("data:") })
        assertFalse(urls.any { "/ignored/" in it })
        assertFalse(urls.any { "navigation-is-not-an-asset" in it })
        assertFalse(urls.any { "comment-only" in it })
    }

    @Test
    fun saverRecursesPersistedStylesheetImportsAndArtworkUntilSettled() = runTest {
        val rootCss = "https://oldschool.runescape.wiki/css/root.css"
        val nestedCss = "https://oldschool.runescape.wiki/css/nested.css"
        val css = mapOf(
            rootCss to "@import 'nested.css'; .hero { background:url('../images/hero.webp') }",
            nestedCss to ".badge { background-image:url('../images/badge.gif') }"
        )
        val attempted = linkedSetOf<String>()
        val saver = ReadingListOfflineAssetSaver(
            fetcher = object : ReadingListAssetFetcher {
                override suspend fun fetchAndPersist(url: String, readingListPageId: Long): Boolean {
                    attempted += url
                    return true
                }

                override suspend fun readPersistedCss(url: String): ReadingListPersistedCss? =
                    css[url]?.let(ReadingListPersistedCss::Content)
            }
        )

        val result = saver.persistAll(
            readingListPageId = 18L,
            html = "<style>@import '/css/root.css';</style>"
        )

        assertTrue(result.isComplete)
        assertEquals(4, result.requiredCount)
        assertEquals(
            setOf(
                rootCss,
                nestedCss,
                "https://oldschool.runescape.wiki/images/hero.webp",
                "https://oldschool.runescape.wiki/images/badge.gif"
            ),
            attempted
        )
    }

    @Test
    fun fontFaceResourcesAreExcludedWhileStylesheetArtworkStillSettles() = runTest {
        val stylesheet = "https://oldschool.runescape.wiki/css/article.css"
        val artwork = "https://oldschool.runescape.wiki/images/panel.png"
        val css = """
            @font-face {
                font-family: 'Article UI';
                src: local('Article UI'), url('../fonts/article-ui.woff2') format('woff2');
            }
            .panel { background-image: url('../images/panel.png'); }
        """.trimIndent()
        assertEquals(
            listOf(artwork),
            ReadingListAssetUrlExtractor.extractCss(css, stylesheet)
        )

        val attempted = mutableListOf<String>()
        val saver = ReadingListOfflineAssetSaver(
            guardedCssFetcher(mapOf(stylesheet to css), attempted)
        )
        val result = saver.persistAll(
            readingListPageId = 181L,
            html = "<link rel='stylesheet' href='/css/article.css'>"
        )

        assertTrue(result.isComplete)
        assertEquals(2, result.requiredCount)
        assertEquals(listOf(stylesheet, artwork), attempted)
        assertFalse(attempted.any { it.endsWith(".woff2") })
    }

    @Test
    fun dataUriSrcsetCommaDoesNotCreatePhantomRelativeAsset() {
        val urls = ReadingListAssetUrlExtractor.extract(
            "<img srcset='data:image/svg+xml,%3Csvg%3E,%3C/svg%3E 1x, /images/real.png 2x'>"
        )

        assertEquals(listOf("https://oldschool.runescape.wiki/images/real.png"), urls)
    }

    @Test
    fun networkIdentityDropsFragmentsButPreservesPathCaseAndQuery() {
        val urls = ReadingListAssetUrlExtractor.extract(
            """
            <svg>
                <image href="/images/Sprite.svg?Revision=2#first"></image>
                <image href="/images/Sprite.svg?Revision=2#second"></image>
            </svg>
            <style>
                .icon { background: url('/images/Sprite.svg?Revision=2#third'); }
                .gradient { mask-image: url(#local-gradient); }
            </style>
            """.trimIndent()
        )

        assertEquals(
            listOf("https://oldschool.runescape.wiki/images/Sprite.svg?Revision=2"),
            urls
        )
    }

    @Test
    fun cyclicStylesheetImportsSettleOnceAndStillSaveArtwork() = runTest {
        val first = "https://oldschool.runescape.wiki/css/first.css"
        val second = "https://oldschool.runescape.wiki/css/second.css"
        val stylesheets = mapOf(
            first to "@import 'second.css';",
            second to "@import 'first.css'; .x{background:url('../images/cycle.gif')}"
        )
        val attempts = mutableListOf<String>()
        val saver = ReadingListOfflineAssetSaver(
            fetcher = guardedCssFetcher(stylesheets, attempts)
        )

        val result = saver.persistAll(19L, "<style>@import '/css/first.css';</style>")

        assertTrue(result.isComplete)
        assertEquals(3, attempts.size)
        assertEquals(1, attempts.count { it == first })
        assertEquals(1, attempts.count { it == second })
        assertTrue("https://oldschool.runescape.wiki/images/cycle.gif" in attempts)
    }

    @Test
    fun stylesheetDepthGuardFailsHonestlyAndCanBeRetried() = runTest {
        val maxDepth = ReadingListOfflineAssetSaver.MAX_CSS_DEPENDENCY_DEPTH
        val stylesheets = (0..maxDepth + 1).associate { depth ->
            val url = "https://oldschool.runescape.wiki/css/depth-$depth.css"
            url to "@import 'depth-${depth + 1}.css';"
        }
        val firstAttempts = mutableListOf<String>()
        val saver = ReadingListOfflineAssetSaver(guardedCssFetcher(stylesheets, firstAttempts))

        val first = saver.persistAll(20L, "<style>@import '/css/depth-0.css';</style>")
        val retryAttempts = mutableListOf<String>()
        val retry = ReadingListOfflineAssetSaver(guardedCssFetcher(stylesheets, retryAttempts))
            .persistAll(20L, "<style>@import '/css/depth-0.css';</style>")

        assertFalse(first.isComplete)
        assertNotNull(first.failedUrls.singleOrNull { it.endsWith("depth-$maxDepth.css") })
        assertEquals(maxDepth + 1, firstAttempts.size)
        assertFalse(retry.isComplete)
        assertEquals(firstAttempts, retryAttempts)
    }

    @Test
    fun stylesheetByteAndDependencyGuardsFailInsteadOfSilentlyTruncating() = runTest {
        val oversizedUrl = "https://oldschool.runescape.wiki/css/oversized.css"
        val oversizedSaver = ReadingListOfflineAssetSaver(
            guardedCssFetcher(
                mapOf(
                    oversizedUrl to "x".repeat(
                        ReadingListOfflineAssetSaver.MAX_CSS_CHARACTERS_PER_STYLESHEET + 1
                    )
                ),
                mutableListOf()
            )
        )
        val oversized = oversizedSaver.persistAll(
            21L,
            "<style>@import '/css/oversized.css';</style>"
        )

        val fanoutUrl = "https://oldschool.runescape.wiki/css/fanout.css"
        val fanoutCss = (0..ReadingListOfflineAssetSaver.MAX_CSS_DISCOVERED_DEPENDENCIES)
            .joinToString("\n") { index -> ".i$index{background:url('/images/$index.png')}" }
        val fanout = ReadingListOfflineAssetSaver(
            guardedCssFetcher(mapOf(fanoutUrl to fanoutCss), mutableListOf())
        ).persistAll(22L, "<style>@import '/css/fanout.css';</style>")

        assertFalse(oversized.isComplete)
        assertEquals(listOf(oversizedUrl), oversized.failedUrls)
        assertFalse(fanout.isComplete)
        assertEquals(listOf(fanoutUrl), fanout.failedUrls)
        assertEquals(1, fanout.requiredCount)
    }

    @Test
    fun saverReportsFailureAndNeverTreatsPartialMediaAsComplete() = runTest {
        val attempted = mutableSetOf<String>()
        val saver = ReadingListOfflineAssetSaver(
            fetcher = ReadingListAssetFetcher { url, _ ->
                attempted += url
                !url.endsWith("failed.gif")
            }
        )

        val result = saver.persistAll(
            readingListPageId = 8L,
            html = "<img src='/ok.png'><img src='/failed.gif'>"
        )

        assertEquals(2, attempted.size)
        assertEquals(2, result.requiredCount)
        assertEquals(1, result.persistedCount)
        assertEquals(listOf("https://oldschool.runescape.wiki/failed.gif"), result.failedUrls)
        assertFalse(result.isComplete)
        assertFalse(
            SavedPageSaveCompletionPolicy.isComplete(
                htmlFetched = true,
                textIndexed = true,
                articlePersisted = true,
                assetsPersisted = result.isComplete
            )
        )
    }

    @Test
    fun cancelingExplicitSavePropagatesAndCannotBecomeComplete() = runTest {
        val started = CompletableDeferred<Unit>()
        val saver = ReadingListOfflineAssetSaver(
            fetcher = ReadingListAssetFetcher { _, _ ->
                started.complete(Unit)
                awaitCancellation()
            }
        )
        val saving = async {
            saver.persistAll(9L, "<img src='/never-responds.png'>")
        }
        started.await()

        saving.cancel()

        val failure = runCatching { saving.await() }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test
    fun resolverReopensOwnedBytesAfterProcessStyleRecreationWithoutNetwork() {
        val storageDir = kotlin.io.path.createTempDirectory("reading-list-assets").toFile()
        try {
            val url = "https://oldschool.runescape.wiki/images/amulet.gif"
            val objectRow = OfflineObject(
                id = 1L,
                url = url,
                lang = "en",
                path = "durable-amulet",
                status = OfflineObject.STATUS_SAVED,
                usedByStr = "|42|",
                saveType = OfflineObject.SAVE_TYPE_READING_LIST
            )
            File(storageDir, "durable-amulet.0").writeText(
                "Content-Type: image/gif\nCache-Control: max-age=3600"
            )
            File(storageDir, "durable-amulet.1").writeBytes(byteArrayOf(1, 2, 3, 4))
            val lookup = { requestedUrl: String, language: String ->
                objectRow.takeIf { requestedUrl == url && language == "en" }
            }

            val firstProcess = ReadingListOfflineAssetResolver(storageDir, lookup)
            assertEquals(listOf<Byte>(1, 2, 3, 4), firstProcess.open(url)!!.stream.use { it.readBytes().toList() })

            val recreatedProcess = ReadingListOfflineAssetResolver(storageDir, lookup)
            val reopened = recreatedProcess.open(url)!!
            assertEquals("image/gif", reopened.mimeType)
            assertEquals(listOf<Byte>(1, 2, 3, 4), reopened.stream.use { it.readBytes().toList() })
        } finally {
            storageDir.deleteRecursively()
        }
    }

    @Test
    fun ownershipMergeAndRemovalPreserveSharedAssetOwners() {
        val merged = ReadingListAssetOwnership.merge("|1|2|", "|2|3|")

        assertEquals("|1|2|3|", merged)
        assertEquals("|1|3|", ReadingListAssetOwnership.remove(merged, 2L))
        assertTrue(ReadingListAssetOwnership.contains(merged, 3L))
    }

    @Test
    fun completionRequiresEveryTextAndMediaStage() {
        assertTrue(SavedPageSaveCompletionPolicy.isComplete(true, true, true, true))
        assertFalse(SavedPageSaveCompletionPolicy.isComplete(true, true, true, false))
        assertFalse(SavedPageSaveCompletionPolicy.isComplete(true, false, true, true))
    }

    @Test
    fun responseValidatorIsQueryAndCaseSafeAndAcceptsRealArtworkFormats() {
        assertValidAsset(
            url = "https://cdn.example/Animation.GIF?version=2",
            contentType = "image/gif",
            bytes = "GIF89a".toByteArray() + byteArrayOf(0, 0)
        )
        assertValidAsset(
            url = "https://cdn.example/art.webp?cache=1",
            contentType = "image/webp",
            bytes = "RIFF0000WEBPVP8 ".toByteArray()
        )
        assertValidAsset(
            url = "https://cdn.example/vector.svg?revision=3",
            contentType = "application/xml; charset=utf-8",
            bytes = "<?xml version='1.0'?><svg xmlns='http://www.w3.org/2000/svg'/>".toByteArray()
        )
        assertValidAsset(
            url = "https://cdn.example/article.css?revision=4",
            contentType = "text/css",
            bytes = ".hero { background: url(art.png); }".toByteArray()
        )
    }

    @Test
    fun responseValidatorRejectsCaptiveHtmlEvenForGifSvgAndCssClaims() {
        val html = "<!doctype html><html><body>Sign in</body></html>".toByteArray()

        assertInvalidAsset("https://cdn.example/animation.gif?token=1", "text/html", html)
        assertInvalidAsset("https://cdn.example/vector.svg", "image/svg+xml", html)
        assertInvalidAsset("https://cdn.example/article.css", "text/css", html)
    }

    private fun guardedCssFetcher(
        stylesheets: Map<String, String>,
        attempts: MutableList<String>
    ) = object : ReadingListAssetFetcher {
        override suspend fun fetchAndPersist(url: String, readingListPageId: Long): Boolean {
            attempts += url
            return true
        }

        override suspend fun readPersistedCss(url: String): ReadingListPersistedCss? =
            stylesheets[url]?.let(ReadingListPersistedCss::Content)
    }

    private fun assertValidAsset(url: String, contentType: String, bytes: ByteArray) {
        val file = kotlin.io.path.createTempFile("valid-reading-list-asset").toFile()
        try {
            file.writeBytes(bytes)
            assertEquals(null, ReadingListAssetResponseValidator.invalidReason(url, contentType, file))
        } finally {
            file.delete()
        }
    }

    private fun assertInvalidAsset(url: String, contentType: String, bytes: ByteArray) {
        val file = kotlin.io.path.createTempFile("invalid-reading-list-asset").toFile()
        try {
            file.writeBytes(bytes)
            assertNotNull(ReadingListAssetResponseValidator.invalidReason(url, contentType, file))
        } finally {
            file.delete()
        }
    }
}
