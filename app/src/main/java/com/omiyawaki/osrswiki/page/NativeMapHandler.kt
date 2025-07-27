package com.omiyawaki.osrswiki.page

import android.util.TypedValue
import android.view.View
import android.webkit.JavascriptInterface
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.ui.map.MapFragment
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Serializable
private data class MapRect(val y: Float, val x: Float, val width: Float, val height: Float)

@Serializable
private data class MapData(val lat: String?, val lon: String?, val zoom: String?, val plane: String?)

class NativeMapHandler(
    private val fragment: PageFragment,
    private val binding: FragmentPageBinding
) {
    val jsInterface = OsrsWikiBridge()

    private val mapContainers = mutableMapOf<String, View>()
    private val offscreenTranslationX = -2000f // A value guaranteed to be off-screen

    @Volatile
    var isHorizontalScrollInProgress = false

    init {
        setupScrollListener()
    }

    inner class OsrsWikiBridge {
        @JavascriptInterface
        fun onMapPlaceholderMeasured(id: String, rectJson: String, mapDataJson: String) {
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                if (!fragment.isAdded) { return@launch }
                try {
                    val rect = Json.decodeFromString<MapRect>(rectJson)
                    val mapData = Json.decodeFromString<MapData>(mapDataJson)
                    preloadMap(id, rect, mapData)
                } catch (e: Exception) {
                    L.e("MAP_DEBUG: Failed to parse map placeholder JSON for pre-loading", e)
                }
            }
        }

        @JavascriptInterface
        fun onCollapsibleToggled(mapId: String, isOpening: Boolean) {
            fragment.view?.post {
                val view = mapContainers[mapId] ?: return@post
                // Instantly move the view on or off screen.
                view.translationX = if (isOpening) 0f else offscreenTranslationX
                val script = "document.getElementById('${mapId}').style.opacity = ${if(isOpening) 0 else 1};"
                binding.pageWebView.evaluateJavascript(script, null)
            }
        }

        @JavascriptInterface
        fun setHorizontalScroll(inProgress: Boolean) {
            isHorizontalScrollInProgress = inProgress
        }
    }

    private fun preloadMap(id: String, rect: MapRect, data: MapData) {
        if (mapContainers.containsKey(id)) {
            return
        }
        val container = FragmentContainerView(fragment.requireContext()).apply {
            this.id = View.generateViewId()
            // The container is VISIBLE but positioned off-screen. This is the
            // only method we've found that reliably triggers pre-rendering.
            visibility = View.VISIBLE
            translationX = offscreenTranslationX
            elevation = 10f
        }
        binding.root.addView(container)
        mapContainers[id] = container

        val params = container.layoutParams as ConstraintLayout.LayoutParams
        val scale = binding.pageWebView.scale
        val correction = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 1f, fragment.resources.displayMetrics
        ).roundToInt()
        params.width = (rect.width * scale).roundToInt() + correction
        params.height = (rect.height * scale).roundToInt()
        params.topMargin = (rect.y * scale).roundToInt()
        params.marginStart = (rect.x * scale).roundToInt()
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        container.layoutParams = params

        val mapFragment = MapFragment.newInstance(
            lat = data.lat,
            lon = data.lon,
            zoom = data.zoom,
            plane = data.plane,
            isPreloading = true
        )
        fragment.childFragmentManager.beginTransaction()
            .replace(container.id, mapFragment)
            .commit()
    }

    private fun setupScrollListener() {
        binding.pageWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            mapContainers.values.forEach { it.translationY = -scrollY.toFloat() }
        }
    }
}
