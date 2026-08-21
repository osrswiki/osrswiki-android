package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.material.appbar.MaterialToolbar
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity

/**
 * Activity to host the OfflineSettingsFragment for offline content and storage settings.
 * Shows only cache, storage, and offline-related options - no appearance settings.
 */
class OfflineSettingsActivity : BaseActivity() {

    companion object {
        /**
         * Creates an Intent to start OfflineSettingsActivity.
         * @param context The Context to use.
         * @return An Intent to start OfflineSettingsActivity.
         */
        fun newIntent(context: Context): Intent {
            return Intent(context, OfflineSettingsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_osrs_settings)
        applyEdgeToEdgeInsets(findViewById(android.R.id.content))

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.osrs_settings_container,
                    OfflineSettingsFragment.newInstance(),
                    OfflineSettingsFragment.TAG
                )
                .commit()
        }

        setupToolbar()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.settings_category_offline_storage)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
