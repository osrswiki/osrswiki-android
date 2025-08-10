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
                val appThemeMode = state[SettingsRepository.KEY_APP_THEME_MODE] as String
                val collapseTablesEnabled = state[SettingsRepository.KEY_COLLAPSE_TABLES] as Boolean

                val tablePreviewItem = SettingItem.InlineTablePreviewSelection(
                    key = SettingsRepository.KEY_COLLAPSE_TABLES,
                    title = "Collapse tables",
                    options = listOf(
                        true to "Collapsed",
                        false to "Expanded"
                    ),
                    currentSelection = collapseTablesEnabled
                )
                
                L.d("SettingsViewModel: Creating InlineTablePreviewSelection with ${tablePreviewItem.options.size} options, currentSelection=${tablePreviewItem.currentSelection}")
                
                val items = listOf(
                    SettingItem.InlineThemeSelection(
                        key = SettingsRepository.KEY_APP_THEME_MODE,
                        title = "App theme",
                        themes = repository.getThemeOptions(),
                        currentSelection = appThemeMode
                    ),
                    SettingItem.CategoryHeader("Content"),
                    tablePreviewItem
                )
                
                L.d("SettingsViewModel: Settings list updated with ${items.size} items")
                _settingsList.value = items
            }
        }
    }

    fun onSwitchSettingToggled(key: String, isChecked: Boolean) {
        // No more switch settings - all replaced with inline previews
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
}