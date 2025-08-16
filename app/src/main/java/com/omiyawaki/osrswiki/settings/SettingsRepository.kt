package com.omiyawaki.osrswiki.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing settings persistence.
 * Uses existing SharedPreferences keys to maintain compatibility.
 */
class SettingsRepository(private val sharedPreferences: SharedPreferences) {

    companion object {
        // Use existing preference keys to maintain compatibility
        const val KEY_APP_THEME_MODE = "app_theme_mode"
        const val KEY_COLLAPSE_TABLES = "collapseTables"
        
        // App theme values - matching original arrays.xml
        const val THEME_AUTO = "auto"
        const val THEME_LIGHT = "light" 
        const val THEME_DARK = "dark"
    }

    private val _settingsState = MutableStateFlow(loadCurrentState())
    val settingsState: StateFlow<Map<String, Any>> = _settingsState

    private fun loadCurrentState(): Map<String, Any> {
        return mapOf(
            KEY_APP_THEME_MODE to getAppThemeMode(),
            KEY_COLLAPSE_TABLES to isCollapseTablesEnabled()
        )
    }

    fun getAppThemeMode(): String {
        return sharedPreferences.getString(KEY_APP_THEME_MODE, THEME_AUTO) ?: THEME_AUTO
    }

    fun setAppThemeMode(mode: String) {
        sharedPreferences.edit().putString(KEY_APP_THEME_MODE, mode).apply()
        refreshState()
    }

    fun isCollapseTablesEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_COLLAPSE_TABLES, true)
    }

    fun setCollapseTablesEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_COLLAPSE_TABLES, enabled).apply()
        refreshState()
    }

    private fun refreshState() {
        _settingsState.value = loadCurrentState()
    }

    fun getThemeDisplayName(themeMode: String): String {
        return when (themeMode) {
            THEME_AUTO -> "Follow system"  // Match original arrays.xml display text
            THEME_LIGHT -> "Light"
            THEME_DARK -> "Dark"
            else -> "Follow system"
        }
    }

    fun getThemeOptions(): List<Pair<String, String>> {
        return listOf(
            THEME_LIGHT to "Light",
            THEME_DARK to "Dark", 
            THEME_AUTO to "Follow system"
        )
    }
}