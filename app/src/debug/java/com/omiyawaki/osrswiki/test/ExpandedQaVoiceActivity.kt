package com.omiyawaki.osrswiki.test

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.SpeechRecognitionManager
import com.omiyawaki.osrswiki.util.createVoiceRecognitionManager

class ExpandedQaVoiceActivity : AppCompatActivity() {
    private lateinit var speechRecognitionManager: SpeechRecognitionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val transcriptField = EditText(this).apply {
            id = R.id.search_edit_text
            isSingleLine = true
        }
        val statusText = TextView(this).apply {
            id = R.id.status_text
        }
        val voiceButton = ImageButton(this).apply {
            id = R.id.voice_search_button
            contentDescription = "Voice search"
            setImageResource(R.drawable.ic_voice_search_24)
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(48, 48, 48, 48)
                addView(
                    transcriptField,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                addView(
                    voiceButton,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                addView(
                    statusText,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        )

        speechRecognitionManager = createVoiceRecognitionManager(
            onResult = { result ->
                transcriptField.setText(result)
                transcriptField.setSelection(result.length)
            },
            onError = { error ->
                statusText.text = "ERROR:$error"
            },
            onPartialResult = { partial ->
                transcriptField.setText(partial)
                transcriptField.setSelection(partial.length)
            },
            onStateChanged = { state ->
                if (!statusText.text.startsWith("ERROR:")) {
                    statusText.text = state.name
                }
            }
        )
        voiceButton.setOnClickListener {
            speechRecognitionManager.startVoiceRecognition()
        }
    }

    override fun onDestroy() {
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.destroy()
        }
        super.onDestroy()
    }
}
