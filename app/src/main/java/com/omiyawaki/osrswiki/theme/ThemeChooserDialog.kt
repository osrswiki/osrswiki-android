package com.omiyawaki.osrswiki.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R // Added this import
import com.omiyawaki.osrswiki.databinding.DialogOsrsThemeChooserBinding // Import view binding

class ThemeChooserDialog : BottomSheetDialogFragment() {

    private var _binding: DialogOsrsThemeChooserBinding? = null
    private val binding get() = _binding!!

    private lateinit var app: OSRSWikiApp

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOsrsThemeChooserBinding.inflate(inflater, container, false)
        app = requireContext().applicationContext as OSRSWikiApp
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupThemeButton(binding.buttonThemeOsrsLight, Theme.OSRS_LIGHT)
        setupThemeButton(binding.buttonThemeOsrsDark, Theme.OSRS_DARK)
        setupThemeButton(binding.buttonThemeWikiLight, Theme.WIKI_LIGHT)
        setupThemeButton(binding.buttonThemeWikiDark, Theme.WIKI_DARK)
        setupThemeButton(binding.buttonThemeWikiBlack, Theme.WIKI_BLACK)

        updateSelectedButton()

        // Optional: If using MaterialButtonToggleGroup, listen to its selection
        binding.themeButtonToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                val selectedTheme = when (checkedId) {
                    R.id.button_theme_osrs_light -> Theme.OSRS_LIGHT
                    R.id.button_theme_osrs_dark -> Theme.OSRS_DARK
                    R.id.button_theme_wiki_light -> Theme.WIKI_LIGHT
                    R.id.button_theme_wiki_dark -> Theme.WIKI_DARK
                    R.id.button_theme_wiki_black -> Theme.WIKI_BLACK
                    else -> null
                }
                selectedTheme?.let {
                    if (app.getCurrentTheme() != it) {
                        app.setCurrentTheme(it, true) // true to persist and notify
                        // The BaseActivity will handle recreation via ThemeChangeEvent
                    }
                }
                // dismiss() // Optionally dismiss after selection
            }
        }
    }

    private fun setupThemeButton(button: MaterialButton, theme: Theme) {
        // Set individual click listeners if not using MaterialButtonToggleGroup exclusively
        // For MaterialButtonToggleGroup, direct click listeners might not be needed if group listener is used.
        // However, this method can be used to initialize the button state or for individual listeners.
        button.setOnClickListener {
            if (app.getCurrentTheme() != theme) {
                app.setCurrentTheme(theme, true) // true to persist and notify
            }
            // dismiss() // Optionally dismiss after selection
        }
    }

    private fun updateSelectedButton() {
        // For MaterialButtonToggleGroup, this sets the initially selected button
        val currentTheme = app.getCurrentTheme()
        val buttonIdToCheck = when (currentTheme) {
            Theme.OSRS_LIGHT -> R.id.button_theme_osrs_light
            Theme.OSRS_DARK -> R.id.button_theme_osrs_dark
            Theme.WIKI_LIGHT -> R.id.button_theme_wiki_light
            Theme.WIKI_DARK -> R.id.button_theme_wiki_dark
            Theme.WIKI_BLACK -> R.id.button_theme_wiki_black
        }
        binding.themeButtonToggleGroup.check(buttonIdToCheck)

        // If not using ToggleGroup and using individual button states (e.g., different stroke)
        // binding.buttonThemeOsrsLight.isChecked = currentTheme == Theme.OSRS_LIGHT
        // ... and so on for other buttons
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ThemeChooserDialog"
        fun newInstance(): ThemeChooserDialog {
            return ThemeChooserDialog()
        }
    }
}
