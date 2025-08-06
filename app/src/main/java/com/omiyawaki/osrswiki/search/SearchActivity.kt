package com.omiyawaki.osrswiki.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.databinding.ActivitySearchBinding
import com.omiyawaki.osrswiki.util.SpeechRecognitionManager
import com.omiyawaki.osrswiki.util.createVoiceRecognitionManager
import com.omiyawaki.osrswiki.util.VoiceSearchAnimationHelper
import com.omiyawaki.osrswiki.util.createVoiceSearchAnimationHelper
import com.omiyawaki.osrswiki.util.FontUtil
import com.omiyawaki.osrswiki.util.log.L

class SearchActivity : BaseActivity() {

    internal lateinit var binding: ActivitySearchBinding
    
    private lateinit var voiceRecognitionManager: SpeechRecognitionManager
    private lateinit var voiceAnimationHelper: VoiceSearchAnimationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.searchToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.searchToolbar.setNavigationOnClickListener { finishAfterTransition() }

        setupFonts()
        setupVoiceSearch()
        
        // Handle voice search query if provided
        val voiceQuery = intent.getStringExtra("query")
        if (!voiceQuery.isNullOrBlank()) {
            binding.searchEditText.setText(voiceQuery)
            binding.searchEditText.setSelection(voiceQuery.length)
        }
        
        // Set focus to the search field
        binding.searchEditText.requestFocus()
    }
    
    private fun setupFonts() {
        L.d("SearchActivity: Setting up fonts...")
        
        // Search input field will use system font
        
        L.d("SearchActivity: Fonts applied to UI elements")
    }

    private fun setupVoiceSearch() {
        // Initialize voice animation helper
        voiceAnimationHelper = binding.voiceSearchButton.createVoiceSearchAnimationHelper()
        
        // Initialize voice recognition manager
        voiceRecognitionManager = createVoiceRecognitionManager(
            onResult = { query ->
                binding.searchEditText.setText(query)
                binding.searchEditText.setSelection(query.length)
            },
            onPartialResult = { partialQuery ->
                // Show real-time transcription
                if (partialQuery.isNotBlank()) {
                    binding.searchEditText.setText(partialQuery)
                    binding.searchEditText.setSelection(partialQuery.length)
                }
            },
            onStateChanged = { state ->
                // Update UI based on speech recognition state
                when (state) {
                    SpeechRecognitionManager.SpeechState.IDLE -> {
                        voiceAnimationHelper.setIdleState()
                    }
                    SpeechRecognitionManager.SpeechState.LISTENING -> {
                        voiceAnimationHelper.setListeningState()
                    }
                    SpeechRecognitionManager.SpeechState.PROCESSING -> {
                        voiceAnimationHelper.setProcessingState()
                    }
                    SpeechRecognitionManager.SpeechState.ERROR -> {
                        voiceAnimationHelper.setErrorState()
                    }
                }
            }
        )
        
        // Set up voice search button
        binding.voiceSearchButton.setOnClickListener {
            voiceRecognitionManager.startVoiceRecognition()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.handlePermissionResult(requestCode, grantResults)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.destroy()
        }
        if (::voiceAnimationHelper.isInitialized) {
            voiceAnimationHelper.cleanup()
        }
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, SearchActivity::class.java)
        }
    }
}
