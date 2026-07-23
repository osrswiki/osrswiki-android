package com.omiyawaki.osrswiki.test

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.ui.map.MapFragment

class ExpandedQaMapActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(container)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(container.id, MapFragment.newInstance(null, null, null, null), MAP_TAG)
                .commitNow()
        }
    }

    fun mapFragment(): MapFragment? {
        return supportFragmentManager.findFragmentByTag(MAP_TAG) as? MapFragment
    }

    companion object {
        private const val MAP_TAG = "expanded-qa-map"
    }
}
