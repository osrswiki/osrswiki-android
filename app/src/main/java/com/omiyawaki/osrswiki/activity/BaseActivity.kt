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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the selected theme BEFORE super.onCreate() and setContentView().
        applyCurrentTheme()
        super.onCreate(savedInstanceState)

        // Listen for theme change events to recreate the activity if the theme is changed
        // while the activity is running.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (applicationContext as OSRSWikiApp).eventBus.collectLatest { event ->
                    if (event is ThemeChangeEvent) {
                        if (event.newTheme.tag != (applicationContext as OSRSWikiApp).getCurrentTheme().tag) {
                            // Update the app-wide theme reference immediately
                            (applicationContext as OSRSWikiApp).setCurrentTheme(event.newTheme, false)
                            ActivityCompat.recreate(this@BaseActivity)
                        }
                    }
                }
            }
        }
    }

    private fun applyCurrentTheme() {
        val osrsWikiApp = applicationContext as OSRSWikiApp
        val selectedTheme = osrsWikiApp.getCurrentTheme()
        // Only apply if it's not the default theme (resourceId 0 or a specific default style)
        // or if you want to ensure even the default is explicitly set.
        // Wikipedia's Light theme had resourceId 0, implying it used the manifest theme.
        // Here, we'll explicitly set all themes chosen by the user.
        setTheme(selectedTheme.resourceId)
    }

    // Call this method from your settings when a user selects a new theme
    protected fun userSelectedNewTheme(newTheme: com.omiyawaki.osrswiki.theme.Theme) {
        val osrsWikiApp = applicationContext as OSRSWikiApp
        if (newTheme.tag != osrsWikiApp.getCurrentTheme().tag) {
            osrsWikiApp.setCurrentTheme(newTheme, true) // true to persist and notify
            // The ThemeChangeEvent listener in onCreate will handle recreating.
        }
    }
}
