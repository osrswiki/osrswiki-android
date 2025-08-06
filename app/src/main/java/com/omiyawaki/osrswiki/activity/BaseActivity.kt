package com.omiyawaki.osrswiki.activity

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.settings.Prefs

abstract class BaseActivity : AppCompatActivity() {

    private var currentThemeId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the selected theme BEFORE super.onCreate() and setContentView().
        applyCurrentTheme()
        super.onCreate(savedInstanceState)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Check if the UI mode has changed (e.g., system light/dark theme switch).
        // If the app's theme is set to "auto", we need to recreate the activity
        // to apply the new system-driven theme.
        if (Prefs.appThemeMode == "auto") {
            // The theme is resolved in OSRSWikiApp.getCurrentTheme() which reads the system
            // state, so simply recreating is sufficient.
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