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

    @Test
    fun articleAudioPlayerPaintsFullWidthChromeAndHidesStackedExtraPlay() {
        val script = asset("web/article_audio_player.js")
        val css = assetFile("styles/android-article-aesthetics.css").readText()

        assertTrue("Shipped chrome must own play/pause, time, and seek.", script.contains("osrs-article-audio-chrome"))
        assertTrue(script.contains("osrs-article-audio-time"))
        assertTrue(script.contains("osrs-article-audio-duration"))
        assertTrue(script.contains("osrs-article-audio-seek"))
        assertTrue(
            "Play control is an icon, not a literal Play label stacked as a themed button.",
            script.contains("createElementNS") && script.contains("Play audio")
        )
        assertTrue(
            "Native Android compact chrome is not the time/seek surface; hide controls when chrome is present.",
            script.contains("removeAttribute") && script.contains("controls")
        )
        assertFalse(
            "Stacked extra Play above the player is the Android 45 QA fail.",
            script.contains("wrap.insertBefore(playBtn, audio)")
        )
        assertFalse(
            "Script must run with a window global; no unguarded Node module.exports.",
            Regex("""(?m)^\s*module\.exports""").containsMatchIn(script)
        )
        assertTrue(
            "IIFE must attach to window, not assume Node.",
            script.contains("typeof window !== 'undefined' ? window : globalThis")
        )
        assertFalse(
            "44px min-height is the tall-chrome QA fail.",
            script.contains("min-height:44px")
        )
        assertFalse(
            "Contrasting --body-border outline is the second-outline QA fail.",
            script.contains("--body-border")
        )
        assertTrue(
            "Chrome height must come from the shared pill token.",
            script.contains("--osrs-article-audio-height")
        )
        assertTrue(
            "Chrome radius must come from the shared half-height token.",
            script.contains("--osrs-article-audio-radius")
        )
        assertTrue(
            "Outer border must be dropped so it cannot read as a second outline.",
            script.contains("border:0")
        )
        assertTrue(css.contains(".osrs-article-audio-chrome"))
        assertTrue(css.contains(".osrs-article-audio-time"))
        assertTrue(css.contains(".osrs-article-audio-seek"))
        assertTrue(
            "Hide leftover stacked play when chrome is present.",
            css.contains(".osrs-article-audio:has(.osrs-article-audio-chrome) > .osrs-article-audio-play")
        )
        assertTrue(
            "Aesthetics must use the shared height token, not a 44px floor.",
            css.contains("--osrs-article-audio-height")
        )
        assertTrue(css.contains("--osrs-article-audio-radius"))
        assertFalse(
            "Aesthetics must not hardcode a 44px chrome/play box.",
            Regex("""\.osrs-article-audio[^{]*\{[^}]*min-height:\s*44px""").containsMatchIn(css)
        )
        val themes = assetFile("styles/themes.css").readText()
        assertTrue(themes.contains("--osrs-article-audio-height: 32px"))
        assertTrue(themes.contains("--osrs-article-audio-radius: calc(var(--osrs-article-audio-height) / 2)"))
        assertTrue(themes.contains("--osrs-article-audio-fill: var(--body-main)"))
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
