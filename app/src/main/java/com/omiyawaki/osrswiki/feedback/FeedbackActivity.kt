package com.omiyawaki.osrswiki.feedback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline

/**
 * Activity to host the FeedbackFragmentSecure for secure Help & Feedback functionality.
 * Uses Google Cloud Function to securely submit feedback without exposing GitHub tokens.
 */
class FeedbackActivity : BaseActivity() {

    companion object {
        /**
         * Creates an Intent to start FeedbackActivity.
         * @param context The Context to use.
         * @return An Intent to start FeedbackActivity.
         */
        fun newIntent(context: Context): Intent {
            return Intent(context, FeedbackActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.feedback_container,
                    FeedbackFragmentSecure.newInstance(),
                    FeedbackFragmentSecure.TAG
                )
                .commit()
        }

        setupToolbar()
        setupFonts()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = getString(R.string.menu_title_feedback)
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