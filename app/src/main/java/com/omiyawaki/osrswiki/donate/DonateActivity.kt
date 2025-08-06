package com.omiyawaki.osrswiki.donate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline

/**
 * Activity to host the DonateFragment for Google Pay donation functionality.
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
        setupFonts()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = getString(R.string.menu_title_donate)
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
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Forward the result to the fragment
        val fragment = supportFragmentManager.findFragmentByTag(DonateFragment.TAG) as? DonateFragment
        fragment?.onActivityResult(requestCode, resultCode, data)
    }
}