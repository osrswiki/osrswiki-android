package com.omiyawaki.osrswiki.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import java.util.Locale

class SpeechRecognitionManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit = { error ->
        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
    }
) {
    
    companion object {
        const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }
    
    fun startVoiceRecognition(launcher: ActivityResultLauncher<Intent>) {
        when {
            !SpeechRecognizer.isRecognitionAvailable(context) -> {
                onError(context.getString(R.string.voice_search_not_available))
                return
            }
            
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED -> {
                
                when (context) {
                    is Activity -> {
                        ActivityCompat.requestPermissions(
                            context,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            REQUEST_RECORD_AUDIO_PERMISSION
                        )
                    }
                    else -> {
                        onError(context.getString(R.string.voice_search_permission_denied))
                    }
                }
                return
            }
            
            else -> {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_search_prompt))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                
                try {
                    launcher.launch(intent)
                } catch (e: Exception) {
                    onError(context.getString(R.string.voice_search_error))
                }
            }
        }
    }
    
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
    
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray, launcher: ActivityResultLauncher<Intent>) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition(launcher)
            } else {
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
    }
): SpeechRecognitionManager {
    return SpeechRecognitionManager(requireContext(), onResult, onError)
}

fun Activity.createVoiceRecognitionManager(
    onResult: (String) -> Unit,
    onError: (String) -> Unit = { error ->
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }
): SpeechRecognitionManager {
    return SpeechRecognitionManager(this, onResult, onError)
}