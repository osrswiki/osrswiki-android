package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity

/**
 * Activity to host the SettingsCategoriesFragment for the main settings categories screen.
 * Shows a list of setting categories (Appearance, Offline Storage, etc.) that lead to 
 * specific settings screens.
 */
class SettingsCategoriesActivity : BaseActivity() {

    companion object {
        /**
         * Creates an Intent to start SettingsCategoriesActivity.
         * @param context The Context to use.
         * @return An Intent to start SettingsCategoriesActivity.
         */
        fun newIntent(context: Context): Intent {
            return Intent(context, SettingsCategoriesActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_categories)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.settings_categories_container,
                    SettingsCategoriesFragment.newInstance(),
                    SettingsCategoriesFragment.TAG
                )
                .commit()
        }

        setupToolbar()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = getString(R.string.menu_title_settings)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}