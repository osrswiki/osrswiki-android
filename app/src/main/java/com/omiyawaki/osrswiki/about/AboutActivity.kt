package com.omiyawaki.osrswiki.about

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline

class AboutActivity : BaseActivity() {

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, AboutActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.about_container,
                    AboutFragment.newInstance(),
                    AboutFragment.TAG
                )
                .commit()
        }

        setupToolbar()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = getString(R.string.menu_title_about)
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
}