package com.omiyawaki.osrswiki.util

import android.content.Context
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceSearchAnimationHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun stateChangesExposeDurableContentDescriptions() {
        val voiceButton = ImageView(context).apply {
            contentDescription = "Voice search"
        }
        val helper = VoiceSearchAnimationHelper(voiceButton)

        helper.setListeningState()
        assertEquals("Voice search listening", voiceButton.contentDescription.toString())

        helper.setProcessingState()
        assertEquals("Voice search processing", voiceButton.contentDescription.toString())

        helper.setErrorState()
        assertEquals("Voice search error", voiceButton.contentDescription.toString())

        helper.setIdleState()
        assertEquals("Voice search", voiceButton.contentDescription.toString())
    }
}
