package com.omiyawaki.osrswiki.page

import android.util.TypedValue
import android.view.View
import android.webkit.JavascriptInterface
import androidx.constraintlayout.widget.ConstraintLayout
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
private data class MapData(
    val y: Float, val x: Float, val width: Float, val height: Float,
    val lat: String?, val lon: String?, val zoom: String?, val plane: String?
)

class NativeMapHandler(
    private val fragment: PageFragment,
    private val binding: FragmentPageBinding
) {
    val jsInterface = OsrsWikiBridge()

    @Volatile
    var isHorizontalScrollInProgress = false

    init {
        setupScrollListener()
    }

    inner class OsrsWikiBridge {
        @JavascriptInterface
        fun onInfoboxExpanded() {
            fragment.view?.post {
                val script = "javascript:findAndShowNativeMap(document.querySelector('.infobox'));"
                binding.pageWebView.evaluateJavascript(script, null)
            }
        }
        
        @JavascriptInterface
        fun onInfoboxCollapsed() {
            fragment.view?.post {
                binding.mapView.visibility = View.GONE
            }
        }

        @JavascriptInterface
        fun onMapFound(json: String) {
            fragment.lifecycleScope.launch {
                try {
                    val mapData = Json.decodeFromString<MapData>(json)
                    showNativeMap(mapData)
                } catch (e: Exception) {
                    L.e("Failed to parse map placeholder JSON", e)
                }
            }
        }

        @JavascriptInterface
        fun setHorizontalScroll(inProgress: Boolean) {
            isHorizontalScrollInProgress = inProgress
        }
    }

    private fun showNativeMap(data: MapData) {
        if (!fragment.isAdded) return

        val container = binding.mapView
        
        // If the map fragment doesn't exist yet, this is the first open.
        if (fragment.childFragmentManager.findFragmentById(R.id.mapView) == null) {
            L.d("First map open. Creating new fragment.")
            
            val params = container.layoutParams as ConstraintLayout.LayoutParams
            val scale = binding.pageWebView.scale
            val correction = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1f, fragment.resources.displayMetrics
            ).roundToInt()
            params.width = (data.width * scale).roundToInt() + correction
            params.height = (data.height * scale).roundToInt()
            params.topMargin = (data.y * scale).roundToInt()
            params.marginStart = (data.x * scale).roundToInt()
            container.layoutParams = params

            val mapFragment = MapFragment.newInstance(
                lat = data.lat,
                lon = data.lon,
                zoom = data.zoom,
                plane = data.plane
            )
            
            fragment.childFragmentManager.beginTransaction()
                .replace(R.id.mapView, mapFragment)
                .commit()
        }
        
        // For both first and subsequent opens, just make sure the container is visible.
        container.visibility = View.VISIBLE
    }

    private fun setupScrollListener() {
        binding.pageWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            binding.mapView.translationY = -scrollY.toFloat()
        }
    }
}
