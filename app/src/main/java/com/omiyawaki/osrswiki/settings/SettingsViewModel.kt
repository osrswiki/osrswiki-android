package com.omiyawaki.osrswiki.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

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

                val items = listOf(
                    SettingItem.CategoryHeader("Appearance"),
                    SettingItem.ListSetting(
                        key = SettingsRepository.KEY_APP_THEME_MODE,
                        title = "App theme",
                        displayValue = repository.getThemeDisplayName(appThemeMode)
                    ),
                    SettingItem.CategoryHeader("Content"),
                    SettingItem.SwitchSetting(
                        key = SettingsRepository.KEY_COLLAPSE_TABLES,
                        title = "Collapse tables",
                        summary = "Collapses infoboxes by default in articles",
                        isChecked = collapseTablesEnabled
                    )
                )
                _settingsList.value = items
            }
        }
    }

    fun onSwitchSettingToggled(key: String, isChecked: Boolean) {
        when (key) {
            SettingsRepository.KEY_COLLAPSE_TABLES -> {
                repository.setCollapseTablesEnabled(isChecked)
            }
        }
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