package com.omiyawaki.osrswiki.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.theme.ThemeAware
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch

/** Conventional native Appearance screen backed by the same typed repository used by readers. */
class AppearanceSettingsFragment : PreferenceFragmentCompat(), ThemeAware {
    private lateinit var viewModel: SettingsViewModel
    private lateinit var themePreference: ListPreference
    private lateinit var collapseTablesPreference: SwitchPreferenceCompat
    private lateinit var textScalePreference: SeekBarPreference
    private lateinit var swipeRightBackPreference: SwitchPreferenceCompat
    private lateinit var swipeLeftContentsPreference: SwitchPreferenceCompat
    private lateinit var floorNumberingPreference: ListPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val repository = SettingsRepository(preferenceManager.sharedPreferences!!)
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(repository)
        )[SettingsViewModel::class.java]

        setPreferencesFromResource(R.xml.preferences_appearance, rootKey)
        themePreference = requirePreference(Prefs.KEY_APP_THEME_MODE)
        collapseTablesPreference = requirePreference(Prefs.KEY_COLLAPSE_TABLES)
        textScalePreference = requirePreference(Prefs.KEY_READER_TEXT_SCALE_PERCENT)
        swipeRightBackPreference = requirePreference(Prefs.KEY_SWIPE_RIGHT_BACK)
        swipeLeftContentsPreference = requirePreference(Prefs.KEY_SWIPE_LEFT_CONTENTS)
        floorNumberingPreference = requirePreference(Prefs.KEY_FLOOR_NUMBERING)

        bindListeners()
        render(viewModel.getCurrentSettings())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.isVerticalScrollBarEnabled = true
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.appearanceSettings.collect(::render)
            }
        }
        if (requireActivity().intent.getBooleanExtra(
                AppearanceSettingsActivity.EXTRA_HIGHLIGHT_FLOOR_NUMBERING,
                false
            )
        ) {
            view.post { highlightFloorNumberingPreference() }
        }
    }

    private fun bindListeners() {
        themePreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            viewModel.onThemeSelected(value as String)
            notifyGlobalThemeChange()
            true
        }
        collapseTablesPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, value ->
                viewModel.onSwitchSettingToggled(Prefs.KEY_COLLAPSE_TABLES, value as Boolean)
                true
            }
        textScalePreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, value ->
                viewModel.onReaderTextScaleChanged(value as Int)
                true
            }
        swipeRightBackPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, value ->
                viewModel.onSwitchSettingToggled(Prefs.KEY_SWIPE_RIGHT_BACK, value as Boolean)
                true
            }
        swipeLeftContentsPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, value ->
                viewModel.onSwitchSettingToggled(Prefs.KEY_SWIPE_LEFT_CONTENTS, value as Boolean)
                true
            }
        floorNumberingPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, value ->
                viewModel.onFloorNumberingSelected(value as String)
                true
            }
    }

    private fun render(settings: AppearancePreferences) {
        themePreference.value = settings.themeMode.persistedValue
        themePreference.summary = getString(
            when (settings.themeMode) {
                AppThemeMode.LIGHT -> R.string.settings_theme_light
                AppThemeMode.DARK -> R.string.settings_theme_dark
                AppThemeMode.FOLLOW_SYSTEM -> R.string.settings_theme_follow_system
            }
        )
        collapseTablesPreference.isChecked = settings.collapseTables
        collapseTablesPreference.summary = getString(
            if (settings.collapseTables) {
                R.string.settings_tables_collapsed_summary
            } else {
                R.string.settings_tables_expanded_summary
            }
        )
        val percent = ReaderTextScale.toPercent(settings.reader.textScale)
        textScalePreference.value = percent
        textScalePreference.summary = getString(R.string.settings_reader_text_size_summary, percent)
        swipeRightBackPreference.isChecked = settings.reader.swipeRightBackEnabled
        swipeLeftContentsPreference.isChecked = settings.reader.swipeLeftContentsEnabled
        floorNumberingPreference.value = settings.floorNumberingMode
        floorNumberingPreference.summary = getString(
            when (settings.floorNumberingMode) {
                "gb" -> R.string.settings_floor_numbering_uk
                "us" -> R.string.settings_floor_numbering_us
                else -> R.string.settings_floor_numbering_auto
            }
        )
    }

    private inline fun <reified T : Preference> requirePreference(key: String): T =
        requireNotNull(findPreference<T>(key)) { "Missing Appearance preference: $key" }

    private fun notifyGlobalThemeChange() {
        LocalBroadcastManager.getInstance(requireContext())
            .sendBroadcast(Intent(ACTION_THEME_CHANGED))
    }

    private fun highlightFloorNumberingPreference() {
        scrollToPreference(floorNumberingPreference)
        listView.post {
            val needle = getString(R.string.settings_floor_numbering_title)
            for (index in 0 until listView.childCount) {
                val row = listView.getChildAt(index) ?: continue
                val matches = java.util.ArrayList<View>()
                row.findViewsWithText(matches, needle, View.FIND_VIEWS_WITH_TEXT)
                if (matches.isEmpty()) continue
                val highlight = ContextCompat.getColor(
                    requireContext(),
                    R.color.osrs_gold_muted
                )
                val original = row.background
                row.setBackgroundColor(highlight)
                row.animate()
                    .alpha(0.55f)
                    .setDuration(180)
                    .withEndAction {
                        row.animate()
                            .alpha(1f)
                            .setDuration(180)
                            .withEndAction {
                                row.animate()
                                    .alpha(0.55f)
                                    .setDuration(180)
                                    .withEndAction {
                                        row.animate()
                                            .alpha(1f)
                                            .setDuration(180)
                                            .withEndAction { row.background = original }
                                            .start()
                                    }
                                    .start()
                            }
                            .start()
                    }
                    .start()
                break
            }
        }
    }

    override fun onThemeChanged() {
        if (isAdded && view != null) {
            listView.adapter?.notifyDataSetChanged()
        }
    }

    companion object {
        const val TAG = "AppearanceSettingsFragment"
        const val ACTION_THEME_CHANGED = "com.omiyawaki.osrswiki.THEME_CHANGED"

        fun newInstance() = AppearanceSettingsFragment()
    }
}
