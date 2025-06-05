package com.omiyawaki.osrswiki

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate // For potential future system-level dark mode sync
import com.omiyawaki.osrswiki.event.ThemeChangeEvent
import com.omiyawaki.osrswiki.theme.Theme
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class OSRSWikiApp : Application() {

    private val _eventBus = MutableSharedFlow<Any>()
    val eventBus = _eventBus.asSharedFlow()

    private lateinit var prefs: SharedPreferences
    private var currentThemeInternal: Theme = Theme.DEFAULT_LIGHT // Default to OSRS_LIGHT

    companion object {
        private const val PREFS_NAME = "osrswiki_app_prefs"
        private const val KEY_CURRENT_THEME_TAG = "current_theme_tag"
        lateinit var instance: OSRSWikiApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadCurrentTheme()

        // Example: If you want to sync with system's DayNight setting for the *initial* default
        // This is optional and depends on how you want your defaults to behave before user selection.
        // val systemNightMode = AppCompatDelegate.getDefaultNightMode()
        // if (prefs.getString(KEY_CURRENT_THEME_TAG, null) == null) { // Only if no user preference yet
        //     currentThemeInternal = if (systemNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
        //         Theme.DEFAULT_DARK
        //     } else {
        //         Theme.DEFAULT_LIGHT
        //     }
        // }
    }

    private fun loadCurrentTheme() {
        val savedThemeTag = prefs.getString(KEY_CURRENT_THEME_TAG, null)
        currentThemeInternal = if (savedThemeTag != null) {
            Theme.ofTag(savedThemeTag) ?: determineDefaultTheme()
        } else {
            determineDefaultTheme()
        }
    }

    private fun determineDefaultTheme(): Theme {
        // Here you can implement logic for the very first app run,
        // potentially checking system night mode. For now, defaults to OSRS_LIGHT.
        // If your manifest theme Theme.OSRSWiki (parenting DayNight) handles system theme for the first run,
        // this can simply be Theme.DEFAULT_LIGHT and user choice will override.
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            Theme.DEFAULT_DARK // Default to OSRS_DARK if system is in dark mode and no user pref
        } else {
            Theme.DEFAULT_LIGHT // Default to OSRS_LIGHT otherwise
        }
    }

    fun getCurrentTheme(): Theme {
        return currentThemeInternal
    }

    fun setCurrentTheme(theme: Theme, persist: Boolean = true) {
        if (currentThemeInternal.tag != theme.tag) {
            currentThemeInternal = theme
            if (persist) {
                prefs.edit().putString(KEY_CURRENT_THEME_TAG, theme.tag).apply()
            }
            // Notify listeners (e.g., BaseActivity to recreate)
            GlobalScope.launch { // Use an appropriate scope
                _eventBus.emit(ThemeChangeEvent(theme))
            }
        }
    }
}
