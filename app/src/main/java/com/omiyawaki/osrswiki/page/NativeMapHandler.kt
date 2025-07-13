package com.omiyawaki.osrswiki.page

import android.util.TypedValue
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
import kotlin.math.roundToInt

@Serializable
private data class RectData(val x: Float, val y: Float, val width: Float, val height: Float)

@Serializable
private data class MapDiagnosticData(
    val y: Float, val x: Float, val width: Float, val height: Float,
    val lat: String?, val lon: String?, val zoom: String?, val plane: String?
)

/**
 * Manages the logic for finding a map placeholder in a WebView,
 * and replacing it with a native MapFragment overlay.
 *
 * @param fragment The host fragment, used for its lifecycle and child fragment manager.
 * @param binding The view binding for the host fragment, used to access UI elements.
 */
class NativeMapHandler(
    private val fragment: PageFragment,
    private val binding: FragmentPageBinding
) {
    /** The JavaScript interface object that will be exposed to the WebView. */
    val jsInterface = NativeMapInterface()

    /** This flag is accessible by the PageFragment to check the scroll state. */
    @Volatile
    var isHorizontalScrollInProgress = false

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
                    // Use a more specific name for the parsed data.
                    val mapPlacementData = Json.decodeFromString<MapDiagnosticData>(json)
                    showNativeMap(mapPlacementData)
                } catch (e: Exception) {
                    L.e("Failed to parse map placeholder JSON", e)
                }
            }
        }

        @JavascriptInterface
        fun onInfoboxExpanded() {
            // When the JS tells us the infobox has expanded,
            // post the action to the main thread and ask the fragment
            // to handle the layout listening.
            fragment.view?.post {
                fragment.handleInfoboxExpansion()
            }
        }

        /**
         * Called from JavaScript to show or hide the native map view.
         * This is used to sync the map's visibility with its collapsible container.
         * @param visible True to show the map, false to hide it.
         */
        @JavascriptInterface
        fun setMapVisibility(visible: Boolean) {
            fragment.view?.post {
                binding.nativeMapContainer.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }

        @JavascriptInterface
        fun setHorizontalScroll(inProgress: Boolean) {
            isHorizontalScrollInProgress = inProgress
        }
    }

    /**
     * Positions and displays the native map container and loads the MapFragment.
     */
    private fun showNativeMap(data: MapDiagnosticData) {
        if (!fragment.isAdded) return

        val container = binding.nativeMapContainer
        val params = container.layoutParams as ConstraintLayout.LayoutParams
        val scale = binding.pageWebView.scale

        // A correction factor to compensate for rounding errors between the WebView's
        // sub-pixel rendering and the native Android layout's integer pixel system.
        val renderingArtifactCorrectionPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            fragment.resources.displayMetrics
        ).roundToInt()

        params.width = (data.width * scale).roundToInt() + renderingArtifactCorrectionPx
        params.height = (data.height * scale).roundToInt()
        params.topMargin = (data.y * scale).roundToInt()
        params.marginStart = (data.x * scale).roundToInt()

        container.layoutParams = params
        container.visibility = View.VISIBLE

        if (fragment.childFragmentManager.findFragmentById(R.id.native_map_container) == null) {
            val mapFragment = MapFragment.newInstance(
                lat = data.lat,
                lon = data.lon,
                zoom = data.zoom,
                plane = data.plane
            )
            fragment.childFragmentManager.beginTransaction()
                .replace(R.id.native_map_container, mapFragment)
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
