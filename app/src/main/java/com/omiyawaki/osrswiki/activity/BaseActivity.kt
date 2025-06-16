package com.omiyawaki.osrswiki.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.event.ThemeChangeEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {

    private var currentThemeId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the selected theme BEFORE super.onCreate() and setContentView().
        applyCurrentTheme()
        super.onCreate(savedInstanceState)

        // Listen for theme change events to recreate the activity if the theme is changed
        // while this activity is in the foreground.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (applicationContext as OSRSWikiApp).eventBus.collectLatest { event ->
                    if (event is ThemeChangeEvent) {
                        // The event signifies a theme change. Recreate the activity.
                        ActivityCompat.recreate(this@BaseActivity)
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun recreate() {
        // Add a fade transition to make the recreation less jarring.
        super.recreate()
    }
    
    override fun onResume() {
        super.onResume()
        // When resuming, check if the global theme has changed since this activity
        // was last created. If so, recreate it to apply the new theme.
        val globalThemeId = (application as OSRSWikiApp).getCurrentTheme().resourceId
        if (currentThemeId != 0 && currentThemeId != globalThemeId) {
            recreate()
        }
    }

    private fun applyCurrentTheme() {
        val osrsWikiApp = applicationContext as OSRSWikiApp
        val selectedTheme = osrsWikiApp.getCurrentTheme()
        // Cache the ID of the theme being applied to this activity instance.
        currentThemeId = selectedTheme.resourceId
        setTheme(selectedTheme.resourceId)
    }
}
