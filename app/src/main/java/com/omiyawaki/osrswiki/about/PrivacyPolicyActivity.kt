package com.omiyawaki.osrswiki.about

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.material.appbar.MaterialToolbar
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity

class PrivacyPolicyActivity : BaseActivity() {

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, PrivacyPolicyActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)
        applyEdgeToEdgeInsets(findViewById(android.R.id.content))

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.privacy_container,
                    PrivacyPolicyFragment.newInstance(),
                    PrivacyPolicyFragment.TAG
                )
                .commit()
        }

        setupToolbar()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.menu_title_privacy)
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
