package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.util.applyRubikUILabel
import com.omiyawaki.osrswiki.util.log.L

class OfflineSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_offline, rootKey)
        
        // No special handling needed currently - just the cache size limit preference
        // Future offline/storage preferences can be added here with their own logic
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFonts()
    }

    private fun setupFonts() {
        L.d("OfflineSettingsFragment: Setting up fonts...")
        
        // Apply fonts to preference TextViews by traversing the RecyclerView
        val recyclerView = listView as? RecyclerView
        recyclerView?.post {
            applyFontsToPreferences(recyclerView)
        }
    }

    private fun applyFontsToPreferences(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val itemView = recyclerView.getChildAt(i)
            applyFontsToPreferenceItem(itemView)
        }
        
        // Also set up a scroll listener to apply fonts to newly visible items
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                for (i in 0 until recyclerView.childCount) {
                    val itemView = recyclerView.getChildAt(i)
                    applyFontsToPreferenceItem(itemView)
                }
            }
        })
        
        L.d("OfflineSettingsFragment: Fonts applied to preference items")
    }

    private fun applyFontsToPreferenceItem(itemView: View) {
        // Find TextViews in the preference item and apply appropriate fonts
        val title = itemView.findViewById<TextView>(android.R.id.title)
        val summary = itemView.findViewById<TextView>(android.R.id.summary)
        
        title?.applyAlegreyaHeadline()
        summary?.applyRubikUILabel()
    }

    companion object {
        const val TAG = "OfflineSettingsFragment"
        fun newInstance(): OfflineSettingsFragment {
            return OfflineSettingsFragment()
        }
    }
}