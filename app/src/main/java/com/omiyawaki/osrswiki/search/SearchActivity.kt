package com.omiyawaki.osrswiki.search

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doOnTextChanged
import android.view.View
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
    
    private var themeChangeReceiver: BroadcastReceiver? = null
    
    private lateinit var voiceRecognitionManager: SpeechRecognitionManager
    private lateinit var voiceAnimationHelper: VoiceSearchAnimationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.hasExtra(EXTRA_DISABLE_FIRST_VIEW_PAINT_PREWARM)) {
            com.omiyawaki.osrswiki.settings.Prefs.disableFirstViewPaintPrewarm =
                intent.getBooleanExtra(EXTRA_DISABLE_FIRST_VIEW_PAINT_PREWARM, false)
        }
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.searchToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.searchToolbar.setNavigationOnClickListener { finishAfterTransition() }
        
        applyEdgeToEdgeInsets(binding.root)

        setupFonts()
        setupVoiceSearch()
        setupClearButton()
        setupThemeChangeReceiver()

        val initialQuery = savedInstanceState?.getString(KEY_SEARCH_QUERY)
            ?: intent.getStringExtra(EXTRA_QUERY)
        if (!initialQuery.isNullOrBlank()) {
            binding.searchEditText.setText(initialQuery)
            binding.searchEditText.setSelection(initialQuery.length)
        }
        
        // Set focus to the search field
        binding.searchEditText.requestFocus()
    }
    
    private fun setupFonts() {
        L.d("SearchActivity: Setting up fonts...")
        
        // Search input field will use system font
        
        L.d("SearchActivity: Fonts applied to UI elements")
    }

    private fun setupClearButton() {
        L.d("SearchActivity: Setting up clear button...")
        
        // Initially hide the clear button
        binding.clearSearchButton.visibility = View.GONE
        
        // Set up text change listener to show/hide clear button
        binding.searchEditText.doOnTextChanged { text, _, _, _ ->
            binding.clearSearchButton.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        
        // Set up clear button click listener
        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.setText("")
            binding.clearSearchButton.visibility = View.GONE
        }
        
        L.d("SearchActivity: Clear button setup complete")
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
    
    private fun setupThemeChangeReceiver() {
        themeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == com.omiyawaki.osrswiki.settings.AppearanceSettingsFragment.ACTION_THEME_CHANGED) {
                    L.d("SearchActivity: Received theme change broadcast")
                    // Apply theme dynamically without recreation
                    applyThemeDynamically()
                }
            }
        }
        
        val filter = IntentFilter(com.omiyawaki.osrswiki.settings.AppearanceSettingsFragment.ACTION_THEME_CHANGED)
        LocalBroadcastManager.getInstance(this).registerReceiver(themeChangeReceiver!!, filter)
        L.d("SearchActivity: Theme change receiver registered")
    }
    
    private fun unregisterThemeChangeReceiver() {
        themeChangeReceiver?.let { receiver ->
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
            themeChangeReceiver = null
            L.d("SearchActivity: Theme change receiver unregistered")
        }
    }
    
    override fun onDestroy() {
        unregisterThemeChangeReceiver()
        super.onDestroy()
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.destroy()
        }
        if (::voiceAnimationHelper.isInitialized) {
            voiceAnimationHelper.cleanup()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SEARCH_QUERY, binding.searchEditText.text?.toString().orEmpty())
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_QUERY = "query"
        const val EXTRA_DISABLE_FIRST_VIEW_PAINT_PREWARM = "osrs_disable_first_view_paint_prewarm"
        private const val KEY_SEARCH_QUERY = "search_query"

        fun newIntent(
            context: Context,
            query: String? = null,
            disableFirstViewPaintPrewarm: Boolean? = null
        ): Intent {
            return Intent(context, SearchActivity::class.java).apply {
                if (!query.isNullOrBlank()) {
                    putExtra(EXTRA_QUERY, query)
                }
                if (disableFirstViewPaintPrewarm != null) {
                    putExtra(EXTRA_DISABLE_FIRST_VIEW_PAINT_PREWARM, disableFirstViewPaintPrewarm)
                }
            }
        }
    }
}
