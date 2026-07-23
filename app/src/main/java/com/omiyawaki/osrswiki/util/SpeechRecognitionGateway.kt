package com.omiyawaki.osrswiki.util

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

interface SpeechRecognitionHandle {
    fun startListening(intent: Intent)
    fun stopListening()
    fun destroy()
}

interface SpeechRecognitionGateway {
    fun isRecognitionAvailable(context: Context): Boolean
    fun createRecognizer(context: Context, listener: RecognitionListener): SpeechRecognitionHandle?
}

object SpeechRecognitionGatewayRegistry {
    @Volatile
    var gateway: SpeechRecognitionGateway = AndroidSpeechRecognitionGateway

    fun reset() {
        gateway = AndroidSpeechRecognitionGateway
    }
}

object AndroidSpeechRecognitionGateway : SpeechRecognitionGateway {
    override fun isRecognitionAvailable(context: Context): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun createRecognizer(context: Context, listener: RecognitionListener): SpeechRecognitionHandle? {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context) ?: return null
        recognizer.setRecognitionListener(listener)
        return AndroidSpeechRecognitionHandle(recognizer)
    }
}

private class AndroidSpeechRecognitionHandle(
    private val recognizer: SpeechRecognizer
) : SpeechRecognitionHandle {
    override fun startListening(intent: Intent) {
        recognizer.startListening(intent)
    }

    override fun stopListening() {
        recognizer.stopListening()
    }

    override fun destroy() {
        recognizer.destroy()
    }
}
