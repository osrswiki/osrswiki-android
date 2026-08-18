package com.omiyawaki.osrswiki.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.omiyawaki.osrswiki.util.log.L

/**
 * ViewModel for the custom settings screen.
 * Manages settings state and handles user interactions.
 */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val appearanceSettings: StateFlow<AppearancePreferences> = repository.settingsState

    private val _settingsList = MutableStateFlow<List<SettingItem>>(emptyList())
    val settingsList: StateFlow<List<SettingItem>> = _settingsList

    private val _showThemeDialog = MutableStateFlow(false)
    val showThemeDialog: StateFlow<Boolean> = _showThemeDialog

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            repository.settingsState.collect { state ->
                val appThemeMode = state.themeMode.persistedValue
                val collapseTablesEnabled = state.collapseTables

                val tablePreviewItem = SettingItem.InlineTablePreviewSelection(
                    key = SettingsRepository.KEY_COLLAPSE_TABLES,
                    title = "Tables",
                    options = listOf(
                        false to "Expanded",
                        true to "Collapsed"
                    ),
                    currentSelection = collapseTablesEnabled
                )
                
                L.d("SettingsViewModel: Creating InlineTablePreviewSelection with ${tablePreviewItem.options.size} options, currentSelection=${tablePreviewItem.currentSelection}")
                
                val items = listOf(
                    SettingItem.InlineThemeSelection(
                        key = SettingsRepository.KEY_APP_THEME_MODE,
                        title = "Theme",
                        themes = repository.getThemeOptions(),
                        currentSelection = appThemeMode
                    ),
                    tablePreviewItem
                )
                
                L.d("SettingsViewModel: Settings list updated with ${items.size} items")
                _settingsList.value = items
            }
        }
    }

    fun onSwitchSettingToggled(key: String, isChecked: Boolean) {
        when (key) {
            SettingsRepository.KEY_COLLAPSE_TABLES ->
                repository.setCollapseTablesEnabled(isChecked)
            SettingsRepository.KEY_SWIPE_RIGHT_BACK ->
                repository.setSwipeRightBackEnabled(isChecked)
            SettingsRepository.KEY_SWIPE_LEFT_CONTENTS ->
                repository.setSwipeLeftContentsEnabled(isChecked)
        }
    }

    fun onTablePreviewSelected(collapseTablesEnabled: Boolean) {
        repository.setCollapseTablesEnabled(collapseTablesEnabled)
    }

    fun onListSettingClicked(key: String) {
        when (key) {
            SettingsRepository.KEY_APP_THEME_MODE -> {
                _showThemeDialog.value = true
            }
        }
    }

    fun onThemeSelected(themeMode: String) {
        repository.setAppThemeMode(themeMode)
        _showThemeDialog.value = false
    }

    fun onDialogDismissed() {
        _showThemeDialog.value = false
    }

    fun getThemeOptions() = repository.getThemeOptions()
    fun getCurrentTheme() = repository.getAppThemeMode()
    fun getCurrentSettings() = repository.currentSettings()

    fun onReaderTextScaleChanged(percent: Int) {
        repository.setReaderTextScalePercent(percent)
    }

    fun onFloorNumberingSelected(mode: String) {
        repository.setFloorNumberingMode(mode)
    }
}
