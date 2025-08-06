package com.omiyawaki.osrswiki.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.log.L
import java.util.Locale

class SpeechRecognitionManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit = { error ->
        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
    },
    private val onPartialResult: (String) -> Unit = {},
    private val onStateChanged: (SpeechState) -> Unit = {},
    fallbackLauncher: ActivityResultLauncher<Intent>? = null
) {
    
    enum class SpeechState {
        IDLE,
        LISTENING,
        PROCESSING,
        ERROR
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var vibrator: Vibrator? = null
    private var lastClickTime: Long = 0
    private var fallbackLauncher: ActivityResultLauncher<Intent>? = null
    private var currentState: SpeechState = SpeechState.IDLE
        set(value) {
            field = value
            L.d("SpeechRecognitionManager: State changed to $value")
            onStateChanged(value)
        }
    
    companion object {
        const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }
    
    init {
        vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        this.fallbackLauncher = fallbackLauncher
    }
    
    fun startVoiceRecognition() {
        L.d("SpeechRecognitionManager: startVoiceRecognition() called, current state: $currentState")
        
        // Debounce rapid clicks
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < 1000) {
            L.d("SpeechRecognitionManager: Ignoring rapid click (debounced)")
            return
        }
        lastClickTime = currentTime
        
        when {
            !SpeechRecognizer.isRecognitionAvailable(context) -> {
                L.e("SpeechRecognitionManager: Speech recognition not available on this device")
                currentState = SpeechState.ERROR
                onError(context.getString(R.string.voice_search_not_available))
                return
            }
            
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED -> {
                L.w("SpeechRecognitionManager: RECORD_AUDIO permission not granted")
                when (context) {
                    is Activity -> {
                        ActivityCompat.requestPermissions(
                            context,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            REQUEST_RECORD_AUDIO_PERMISSION
                        )
                    }
                    else -> {
                        currentState = SpeechState.ERROR
                        onError(context.getString(R.string.voice_search_permission_denied))
                    }
                }
                return
            }
            
            currentState == SpeechState.LISTENING -> {
                L.d("SpeechRecognitionManager: Currently listening, stopping recognition")
                stopListening()
                return
            }
            
            currentState != SpeechState.IDLE -> {
                L.d("SpeechRecognitionManager: Already in state $currentState, ignoring request")
                return
            }
            
            else -> {
                startListening()
            }
        }
    }
    
    private fun startListening() {
        L.d("SpeechRecognitionManager: startListening() called")
        try {
            // Provide haptic feedback (safe, won't crash if permission missing)
            try {
                vibrator?.let {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(50)
                    }
                }
                L.d("SpeechRecognitionManager: Haptic feedback provided")
            } catch (e: SecurityException) {
                L.w("SpeechRecognitionManager: No VIBRATE permission, skipping haptic feedback")
            } catch (e: Exception) {
                L.w("SpeechRecognitionManager: Failed to provide haptic feedback")
            }
            
            // Clean up any existing recognizer
            speechRecognizer?.destroy()
            
            // Create new speech recognizer
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            
            if (speechRecognizer == null) {
                L.e("SpeechRecognitionManager: Failed to create SpeechRecognizer - service not available, trying fallback")
                tryFallbackRecognition()
                return
            }
            
            L.d("SpeechRecognitionManager: SpeechRecognizer created successfully")
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            
            L.d("SpeechRecognitionManager: Starting speech recognition")
            currentState = SpeechState.LISTENING
            speechRecognizer?.startListening(intent)
            
        } catch (e: Exception) {
            L.e("SpeechRecognitionManager: Exception in startListening(), trying fallback", e)
            tryFallbackRecognition()
        }
    }
    
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                currentState = SpeechState.LISTENING
            }
            
            override fun onBeginningOfSpeech() {
                // Speech input has begun
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // Sound level changed - could be used for volume visualization
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
                // Audio buffer received
            }
            
            override fun onEndOfSpeech() {
                currentState = SpeechState.PROCESSING
            }
            
            override fun onError(error: Int) {
                L.e("SpeechRecognitionManager: Recognition error occurred, code: $error")
                currentState = SpeechState.ERROR
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> {
                        L.e("SpeechRecognitionManager: ERROR_AUDIO - Audio recording error")
                        "Microphone error. Please check if another app is using the microphone."
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {
                        L.e("SpeechRecognitionManager: ERROR_CLIENT - Client side error")
                        "Speech recognition client error. Please try again."
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        L.e("SpeechRecognitionManager: ERROR_INSUFFICIENT_PERMISSIONS")
                        context.getString(R.string.voice_search_permission_denied)
                    }
                    SpeechRecognizer.ERROR_NETWORK -> {
                        L.e("SpeechRecognitionManager: ERROR_NETWORK - Network error")
                        "Network error. Please check your internet connection and try again."
                    }
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        L.e("SpeechRecognitionManager: ERROR_NETWORK_TIMEOUT")
                        "Network timeout. Please check your connection and try again."
                    }
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        L.w("SpeechRecognitionManager: ERROR_NO_MATCH - No speech detected")
                        context.getString(R.string.voice_search_no_results)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        L.w("SpeechRecognitionManager: ERROR_RECOGNIZER_BUSY")
                        "Speech recognizer is busy. Please wait a moment and try again."
                    }
                    SpeechRecognizer.ERROR_SERVER -> {
                        L.e("SpeechRecognitionManager: ERROR_SERVER - Server error")
                        "Speech recognition service error. Please try again later."
                    }
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        L.w("SpeechRecognitionManager: ERROR_SPEECH_TIMEOUT")
                        "No speech detected. Please speak clearly and try again."
                    }
                    else -> {
                        L.e("SpeechRecognitionManager: Unknown error code: $error")
                        "Speech recognition error (code: $error). Please try again."
                    }
                }
                onError(errorMessage)
                resetState()
            }
            
            override fun onResults(results: Bundle?) {
                L.d("SpeechRecognitionManager: onResults called")
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                L.d("SpeechRecognitionManager: Recognition results: $matches")
                if (!matches.isNullOrEmpty()) {
                    val result = matches[0]
                    L.d("SpeechRecognitionManager: Final result: '$result'")
                    onResult(result)
                } else {
                    L.w("SpeechRecognitionManager: No results in onResults")
                    onError(context.getString(R.string.voice_search_no_results))
                }
                resetState()
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialResult = matches[0]
                    L.d("SpeechRecognitionManager: Partial result: '$partialResult'")
                    onPartialResult(partialResult)
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
                // Speech recognition event
            }
        }
    }
    
    fun stopListening() {
        L.d("SpeechRecognitionManager: stopListening() called")
        if (currentState == SpeechState.LISTENING) {
            try {
                currentState = SpeechState.PROCESSING
                speechRecognizer?.stopListening()
                L.d("SpeechRecognitionManager: Speech recognizer stopped, waiting for results")
            } catch (e: Exception) {
                L.e("SpeechRecognitionManager: Exception in stopListening()", e)
                currentState = SpeechState.ERROR
                onError("Failed to stop speech recognition: ${e.message ?: "Unknown error"}")
                resetState()
            }
        } else {
            L.w("SpeechRecognitionManager: stopListening() called but not in LISTENING state (current: $currentState)")
        }
    }
    
    private fun resetState() {
        currentState = SpeechState.IDLE
    }
    
    private fun tryFallbackRecognition() {
        L.d("SpeechRecognitionManager: Attempting fallback to RecognizerIntent")
        val launcher = fallbackLauncher
        if (launcher != null) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_search_prompt))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                
                currentState = SpeechState.LISTENING
                launcher.launch(intent)
                L.d("SpeechRecognitionManager: Fallback intent launched successfully")
            } catch (e: Exception) {
                L.e("SpeechRecognitionManager: Fallback also failed", e)
                currentState = SpeechState.ERROR
                onError("Speech recognition is not available on this device. Please ensure Google app or Google Play Services is installed.")
            }
        } else {
            L.e("SpeechRecognitionManager: No fallback launcher available")
            currentState = SpeechState.ERROR
            onError("Speech recognition service is unavailable. Please install Google app or ensure Google Play Services is up to date.")
        }
    }
    
    fun destroy() {
        L.d("SpeechRecognitionManager: destroy() called")
        speechRecognizer?.destroy()
        speechRecognizer = null
        fallbackLauncher = null
        currentState = SpeechState.IDLE
    }
    
    // Legacy method for backward compatibility - will be removed
    @Deprecated("Use startVoiceRecognition() instead")
    fun handleActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                onResult(results[0])
            } else {
                onError(context.getString(R.string.voice_search_no_results))
            }
        }
    }
    
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        L.d("SpeechRecognitionManager: handlePermissionResult called, code: $requestCode")
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                L.d("SpeechRecognitionManager: Permission granted, starting recognition")
                startVoiceRecognition()
            } else {
                L.w("SpeechRecognitionManager: Permission denied")
                currentState = SpeechState.ERROR
                onError(context.getString(R.string.voice_search_permission_denied))
            }
        }
    }
}

// Extension functions for easier integration
fun Fragment.createVoiceRecognitionManager(
    onResult: (String) -> Unit,
    onError: (String) -> Unit = { error ->
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
    },
    onPartialResult: (String) -> Unit = {},
    onStateChanged: (SpeechRecognitionManager.SpeechState) -> Unit = {},
    fallbackLauncher: ActivityResultLauncher<Intent>? = null
): SpeechRecognitionManager {
    return SpeechRecognitionManager(requireContext(), onResult, onError, onPartialResult, onStateChanged, fallbackLauncher)
}

fun Activity.createVoiceRecognitionManager(
    onResult: (String) -> Unit,
    onError: (String) -> Unit = { error ->
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    },
    onPartialResult: (String) -> Unit = {},
    onStateChanged: (SpeechRecognitionManager.SpeechState) -> Unit = {},
    fallbackLauncher: ActivityResultLauncher<Intent>? = null
): SpeechRecognitionManager {
    return SpeechRecognitionManager(this, onResult, onError, onPartialResult, onStateChanged, fallbackLauncher)
}