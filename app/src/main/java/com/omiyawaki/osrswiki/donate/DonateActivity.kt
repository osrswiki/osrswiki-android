package com.omiyawaki.osrswiki.donate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.material.appbar.MaterialToolbar
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity

/**
 * Activity to host the DonateFragment for Google Play In-App Billing donation functionality.
 */
class DonateActivity : BaseActivity() {

    companion object {
        /**
         * Creates an Intent to start DonateActivity.
         * @param context The Context to use.
         * @return An Intent to start DonateActivity.
         */
        fun newIntent(context: Context): Intent {
            return Intent(context, DonateActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_donate)
        applyEdgeToEdgeInsets(findViewById(android.R.id.content))

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.donate_container,
                    DonateFragment.newInstance(),
                    DonateFragment.TAG
                )
                .commit()
        }

        setupToolbar()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.menu_title_donate)
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
