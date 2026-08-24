package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleAudioPlayerContractTest {

    @Test
    fun articleAudioPlayerPrefersMpegAndSurfacesErrorInsteadOfInfiniteLoading() {
        val script = asset("web/article_audio_player.js")
        val builder = source("page/PageHtmlBuilder.kt")

        assertTrue(script.contains("osrs-article-audio"))
        assertTrue(script.contains("osrs-article-audio-play"))
        assertTrue(script.contains("audio/mpeg"))
        assertTrue(script.contains("osrs-article-audio-error"))
        assertTrue(script.contains("Audio unavailable"))
        assertTrue(
            "TimedMediaHandler hang: leave loading after a bounded wait.",
            script.contains("osrsArticleAudioLoadingTimeoutMs")
        )
        assertTrue(builder.contains("web/article_audio_player.js"))
        assertFalse(script.contains("ext.gadget.audioplayer-core"))
    }

    @Test
    fun articleAudioPlayerRewritesInfoboxSourcesToPreferSavedMpeg() {
        val script = asset("web/article_audio_player.js")
        assertTrue(script.contains("preferredMpegSource"))
        assertTrue(script.contains("infobox-media-player"))
        assertTrue(script.contains("audio.mw-file-element"))
    }

    private fun asset(path: String): String = assetFile(path).readText()

    private fun source(path: String): String {
        val file = File("src/main/java/com/omiyawaki/osrswiki", path).takeIf { it.exists() }
            ?: File("app/src/main/java/com/omiyawaki/osrswiki", path)
        return file.readText()
    }

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets", path),
        File("app/src/main/assets", path)
    ).firstOrNull { it.exists() } ?: error("Missing Android asset: $path")
}
