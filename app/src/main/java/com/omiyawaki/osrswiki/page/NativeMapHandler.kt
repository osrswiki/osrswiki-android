package com.omiyawaki.osrswiki.page

import android.view.View
import android.webkit.JavascriptInterface
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.ui.map.MapFragment
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class MapPlaceholder(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Manages the logic for finding a map placeholder in a WebView,
 * and replacing it with a native MapFragment overlay.
 *
 * @param fragment The host fragment, used for its lifecycle and child fragment manager.
 * @param binding The view binding for the host fragment, used to access UI elements.
 */
class NativeMapHandler(
    private val fragment: Fragment,
    private val binding: FragmentPageBinding
) {
    /** The JavaScript interface object that will be exposed to the WebView. */
    val jsInterface = NativeMapInterface()

    init {
        setupScrollListener()
    }

    /**
     * An inner class containing the method that will be callable from JavaScript.
     */
    inner class NativeMapInterface {
        @JavascriptInterface
        fun onMapFound(json: String) {
            fragment.lifecycleScope.launch {
                try {
                    val placeholder = Json.decodeFromString<MapPlaceholder>(json)
                    showNativeMap(placeholder)
                } catch (e: Exception) {
                    L.e("Failed to parse map placeholder JSON", e)
                }
            }
        }
    }

    /**
     * Positions and displays the native map container and loads the MapFragment.
     */
    private fun showNativeMap(placeholder: MapPlaceholder) {
        if (!fragment.isAdded) return

        val container = binding.nativeMapContainer
        val params = container.layoutParams as ConstraintLayout.LayoutParams
        val scale = binding.pageWebView.scale

        // Apply the WebView's scale to convert CSS pixels from JS to device pixels for the layout.
        params.width = (placeholder.width * scale).toInt()
        params.height = (placeholder.height * scale).toInt()
        params.topMargin = (placeholder.y * scale).toInt()
        params.marginStart = (placeholder.x * scale).toInt()

        container.layoutParams = params
        container.visibility = View.VISIBLE

        // Add the MapFragment if it's not already there.
        if (fragment.childFragmentManager.findFragmentById(R.id.native_map_container) == null) {
            fragment.childFragmentManager.beginTransaction()
                .replace(R.id.native_map_container, MapFragment())
                .commit()
        }
    }

    /**
     * Sets up a listener to sync the native map's position with the WebView's scroll.
     */
    private fun setupScrollListener() {
        binding.pageWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // Keep the map overlay in sync with the WebView's scroll position.
            // The initial position is set by the top margin. The translationY
            // adjusts this position as the user scrolls.
            binding.nativeMapContainer.translationY = -scrollY.toFloat()
        }
    }
}
