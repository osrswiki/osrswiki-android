package com.omiyawaki.osrswiki.page

import android.util.TypedValue
import android.view.View
import android.webkit.JavascriptInterface
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentContainerView
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
            L.d("MAP_DEBUG: Native onMapPlaceholderMeasured received. ID: $id, Rect: $rectJson")
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                L.d("MAP_DEBUG: Coroutine for $id started. Fragment isAdded: ${fragment.isAdded}")
                if (!fragment.isAdded) {
                    L.w("MAP_DEBUG: Fragment not added, aborting preload for $id.")
                    return@launch
                }
                try {
                    val rect = Json.decodeFromString<MapRect>(rectJson)
                    val mapData = Json.decodeFromString<MapData>(mapDataJson)
                    L.d("MAP_DEBUG: Parsed data for $id. Calling preloadMap.")
                    preloadMap(id, rect, mapData)
                } catch (e: Exception) {
                    L.e("MAP_DEBUG: Failed to parse map placeholder JSON for pre-loading", e)
                }
            }
        }

        @JavascriptInterface
        fun onCollapsibleToggled(mapId: String, isOpening: Boolean) {
            L.d("MAP_DEBUG: NATIVE METHOD ENTRY: onCollapsibleToggled received. ID: $mapId, IsOpening: $isOpening")
            fragment.view?.post {
                val view = mapContainers[mapId]
                if (view == null) {
                    L.w("MAP_DEBUG: No map container found for ID: $mapId. Cannot toggle visibility.")
                    return@post
                }

                if (isOpening) {
                    L.d("MAP_DEBUG: Animating map container for ID: $mapId into view.")
                    view.animate().translationX(0f).setDuration(200).start()
                    // Also make the original placeholder invisible in the WebView
                    val script = "document.getElementById('${mapId}').style.opacity = '0';"
                    binding.pageWebView.evaluateJavascript(script, null)
                } else {
                    L.d("MAP_DEBUG: Animating map container for ID: $mapId out of view.")
                    view.animate().translationX(offscreenTranslationX).setDuration(200).start()
                }
            }
        }

        @JavascriptInterface
        fun setHorizontalScroll(inProgress: Boolean) {
            isHorizontalScrollInProgress = inProgress
        }
    }

    private fun preloadMap(id: String, rect: MapRect, data: MapData) {
        if (mapContainers.containsKey(id)) {
            L.w("MAP_DEBUG: Map container for $id already exists, aborting.")
            return
        }
        L.d("MAP_DEBUG: PRELOAD_EXEC: Preloading map for ID: $id")

        val container = FragmentContainerView(fragment.requireContext()).apply {
            this.id = View.generateViewId()
            // The container is now fully opaque but positioned off-screen.
            alpha = 1f
            translationX = offscreenTranslationX
            visibility = View.VISIBLE
            elevation = 10f
        }
        L.d("MAP_DEBUG: PRELOAD_EXEC: Created FragmentContainerView for $id with view ID ${container.id}")

        binding.root.addView(container)
        mapContainers[id] = container
        L.d("MAP_DEBUG: PRELOAD_EXEC: Added container for $id to root view. Total containers: ${mapContainers.size}")

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
        L.d("MAP_DEBUG: PRELOAD_EXEC: Layout params for $id: width=${params.width}, height=${params.height}, topMargin=${params.topMargin}, startMargin=${params.marginStart}")

        val mapFragment = MapFragment.newInstance(
            lat = data.lat,
            lon = data.lon,
            zoom = data.zoom,
            plane = data.plane
        )

        fragment.childFragmentManager.beginTransaction()
            .replace(container.id, mapFragment)
            .commit()
        L.d("MAP_DEBUG: PRELOAD_EXEC: Committed MapFragment transaction for $id into container ${container.id}")
    }

    private fun setupScrollListener() {
        binding.pageWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // Apply scroll translation to all map containers
            mapContainers.values.forEach { it.translationY = -scrollY.toFloat() }
        }
    }
}
