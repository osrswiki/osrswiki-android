package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity

/**
 * Activity to host the AppearanceSettingsFragment for appearance-specific settings.
 * Shows only theme, color, and display options - no offline/storage settings.
 */
class AppearanceSettingsActivity : BaseActivity() {

    companion object {
        /**
         * Creates an Intent to start AppearanceSettingsActivity.
         * @param context The Context to use.
         * @return An Intent to start AppearanceSettingsActivity.
         */
        fun newIntent(context: Context): Intent {
            return Intent(context, AppearanceSettingsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appearance_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.appearance_settings_container, 
                    AppearanceSettingsFragment.newInstance(), 
                    AppearanceSettingsFragment.TAG
                )
                .commit()
        }

        setupToolbar()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = getString(R.string.settings_category_appearance)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}