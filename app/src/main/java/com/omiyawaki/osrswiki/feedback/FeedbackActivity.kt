package com.omiyawaki.osrswiki.feedback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.material.appbar.MaterialToolbar
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity

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
        applyEdgeToEdgeInsets(findViewById(android.R.id.content))

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
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.feedback_nav_title)
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
