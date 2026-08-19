package com.omiyawaki.osrswiki.settings

import android.content.SharedPreferences
import com.omiyawaki.osrswiki.page.osrsArticleFloorNumberingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing settings persistence.
 * Uses existing SharedPreferences keys to maintain compatibility.
 */
class SettingsRepository(private val sharedPreferences: SharedPreferences) {

    companion object {
        // Use existing preference keys to maintain compatibility
        const val KEY_APP_THEME_MODE = AppearancePreferenceKeys.APP_THEME_MODE
        const val KEY_COLLAPSE_TABLES = AppearancePreferenceKeys.COLLAPSE_TABLES
        const val KEY_WRAP_TABLE_CELLS = AppearancePreferenceKeys.WRAP_TABLE_CELLS
        const val KEY_READER_TEXT_SCALE_PERCENT = AppearancePreferenceKeys.READER_TEXT_SCALE_PERCENT
        const val KEY_SWIPE_RIGHT_BACK = AppearancePreferenceKeys.SWIPE_RIGHT_BACK
        const val KEY_SWIPE_LEFT_CONTENTS = AppearancePreferenceKeys.SWIPE_LEFT_CONTENTS
        const val KEY_FLOOR_NUMBERING = AppearancePreferenceKeys.FLOOR_NUMBERING
        
        // App theme values - matching original arrays.xml
        const val THEME_AUTO = "auto"
        const val THEME_LIGHT = "light" 
        const val THEME_DARK = "dark"
    }

    private val _settingsState = MutableStateFlow(loadCurrentState())
    val settingsState: StateFlow<AppearancePreferences> = _settingsState

    private fun loadCurrentState(): AppearancePreferences =
        AppearancePreferencesCodec.readAndMigrate(sharedPreferences)

    fun currentSettings(): AppearancePreferences = loadCurrentState()

    fun getAppThemeMode(): String {
        return loadCurrentState().themeMode.persistedValue
    }

    fun setAppThemeMode(mode: String) {
        sharedPreferences.edit()
            .putString(KEY_APP_THEME_MODE, AppThemeMode.fromPersistedValue(mode).persistedValue)
            .apply()
        refreshState()
    }

    fun isCollapseTablesEnabled(): Boolean {
        return loadCurrentState().collapseTables
    }

    fun setCollapseTablesEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_COLLAPSE_TABLES, enabled).apply()
        refreshState()
    }

    fun wrapTableCells(): Boolean = loadCurrentState().wrapTableCells

    fun setWrapTableCellsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_WRAP_TABLE_CELLS, enabled).apply()
        refreshState()
    }

    fun getReaderTextScale(): Float = loadCurrentState().reader.textScale

    fun setReaderTextScale(scale: Float) {
        sharedPreferences.edit()
            .putInt(KEY_READER_TEXT_SCALE_PERCENT, ReaderTextScale.toPercent(scale))
            .apply()
        refreshState()
    }

    fun setReaderTextScalePercent(percent: Int) {
        setReaderTextScale(ReaderTextScale.fromPercent(percent))
    }

    fun isSwipeRightBackEnabled(): Boolean =
        loadCurrentState().reader.swipeRightBackEnabled

    fun setSwipeRightBackEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SWIPE_RIGHT_BACK, enabled).apply()
        refreshState()
    }

    fun isSwipeLeftContentsEnabled(): Boolean =
        loadCurrentState().reader.swipeLeftContentsEnabled

    fun setSwipeLeftContentsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SWIPE_LEFT_CONTENTS, enabled).apply()
        refreshState()
    }

    fun getFloorNumberingMode(): String = loadCurrentState().floorNumberingMode

    fun setFloorNumberingMode(mode: String) {
        sharedPreferences.edit()
            .putString(
                KEY_FLOOR_NUMBERING,
                osrsArticleFloorNumberingMode.fromPersisted(mode).persistedValue
            )
            .apply()
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
