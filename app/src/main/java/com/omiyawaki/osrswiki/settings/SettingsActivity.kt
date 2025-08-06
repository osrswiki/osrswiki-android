package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity // Ensure this import is correct
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline

/**
 * Activity to host the SettingsFragment for application settings.
 * Mirrors Wikipedia's SettingsActivity pattern by hosting a fragment.
 */
class SettingsActivity : BaseActivity() {

    companion object {
        /**
         * Creates an Intent to start SettingsActivity.
         * @param context The Context to use.
         * @return An Intent to start SettingsActivity.
         */
        fun newIntent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment.newInstance(), SettingsFragment.TAG)
                .commit()
        }

        setupToolbar()
        setupFonts()
    }

    private fun setupToolbar() {
        // Assuming BaseActivity sets up a support action bar.
        // If your BaseActivity or themes handle toolbar setup, this might just be for title and Up button.
        supportActionBar?.apply {
            title = getString(R.string.menu_title_settings) // Or a more specific "Settings" string if you have one
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    private fun setupFonts() {
        // Apply fonts to action bar title
        supportActionBar?.let { actionBar ->
            // Find the action bar title TextView and apply font
            val titleId = resources.getIdentifier("action_bar_title", "id", "android")
            if (titleId > 0) {
                findViewById<TextView>(titleId)?.applyAlegreyaHeadline()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        // Handles the action bar's Up/Home button.
        // If a custom behavior is needed (e.g., specific back stack manipulation), implement it here.
        // Otherwise, default behavior is to finish the activity.
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
