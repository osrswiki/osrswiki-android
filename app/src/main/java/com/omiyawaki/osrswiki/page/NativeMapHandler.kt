package com.omiyawaki.osrswiki.page

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.ui.map.StandardNavigationMapFragment
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Serializable
private data class MapRect(val y: Float, val x: Float, val width: Float, val height: Float)

@Serializable
private data class MapData(val lat: String?, val lon: String?, val zoom: String?, val plane: String?)

private data class PendingMapPlaceholder(val rect: MapRect, val data: MapData)

class NativeMapHandler(
    private val fragment: PageFragment,
    private val binding: FragmentPageBinding
) {
    val jsInterface = OsrsWikiBridge()

    private val pendingMapPlaceholders = mutableMapOf<String, PendingMapPlaceholder>()
    private val mapContainers = mutableMapOf<String, View>()
    private val offscreenTranslationX = -2000f // A value guaranteed to be off-screen
    private var isCleanedUp = false

    @Volatile
    var isHorizontalScrollInProgress = false

    init {
        setupScrollListener()
    }

    inner class OsrsWikiBridge {
        @JavascriptInterface
        fun onMapPlaceholderMeasured(id: String, rectJson: String, mapDataJson: String) {
            if (isCleanedUp) {
                return
            }
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                if (!fragment.isAdded || isCleanedUp) { return@launch }
                try {
                    val rect = Json.decodeFromString<MapRect>(rectJson)
                    val mapData = Json.decodeFromString<MapData>(mapDataJson)
                    rememberMapPlaceholder(id, rect, mapData)
                } catch (e: Exception) {
                    L.e("MAP_DEBUG: Failed to parse map placeholder JSON for pre-loading", e)
                }
            }
        }

        @JavascriptInterface
        fun onCollapsibleToggled(mapId: String, isOpening: Boolean) {
            if (isCleanedUp) {
                return
            }
            fragment.view?.post {
                if (isCleanedUp) {
                    return@post
                }
                val view = if (isOpening) {
                    ensureMapContainer(mapId)
                } else {
                    mapContainers[mapId]
                } ?: return@post
                // Instantly move the view on or off screen.
                view.translationX = if (isOpening) 0f else offscreenTranslationX
                val script = "document.getElementById('${mapId}').style.opacity = ${if(isOpening) 0 else 1};"
                binding.pageWebView.evaluateJavascript(script, null)
            }
        }

        @JavascriptInterface
        fun setHorizontalScroll(inProgress: Boolean) {
            if (isCleanedUp) {
                return
            }
            L.d("NativeMapHandler: setHorizontalScroll called, inProgress=$inProgress")
            isHorizontalScrollInProgress = inProgress
        }
        
        @JavascriptInterface
        fun log(message: String) {
            L.d("JS: $message")
        }
    }

    private fun rememberMapPlaceholder(id: String, rect: MapRect, data: MapData) {
        if (isCleanedUp) {
            return
        }
        pendingMapPlaceholders[id] = PendingMapPlaceholder(rect, data)
    }

    private fun ensureMapContainer(id: String): View? {
        if (isCleanedUp) {
            return null
        }
        mapContainers[id]?.let { return it }
        val pending = pendingMapPlaceholders[id] ?: return null
        return createMapContainer(id, pending.rect, pending.data)
    }

    private fun createMapContainer(id: String, rect: MapRect, data: MapData): View? {
        if (isCleanedUp) {
            return null
        }
        if (mapContainers.containsKey(id)) {
            return mapContainers[id]
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

        val mapFragment = StandardNavigationMapFragment.newInstance(
            lat = data.lat,
            lon = data.lon,
            zoom = data.zoom,
            plane = data.plane
        )
        fragment.childFragmentManager.beginTransaction()
            .replace(container.id, mapFragment)
            .commit()
        return container
    }

    private fun setupScrollListener() {
        binding.pageWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            mapContainers.values.forEach { it.translationY = -scrollY.toFloat() }
        }
    }
    
    /**
     * Cleans up all map containers when the page is destroyed.
     * This prevents map views from bleeding through to other screens.
     */
    fun cleanup() {
        if (isCleanedUp) {
            return
        }
        isCleanedUp = true
        L.d("NativeMapHandler: Cleaning up ${mapContainers.size} map containers")
        val containers = mapContainers.values.toList()

        try {
            val childFragmentManager = fragment.childFragmentManager
            val transaction = childFragmentManager.beginTransaction()
            var hasFragments = false
            containers.forEach { container ->
                childFragmentManager.findFragmentById(container.id)?.let { childFragment ->
                    transaction.remove(childFragment)
                    hasFragments = true
                }
            }
            if (hasFragments) {
                transaction.commitNowAllowingStateLoss()
            }
        } catch (e: Exception) {
            L.e("NativeMapHandler: Error removing embedded map fragments during cleanup", e)
        }
        
        // Remove all map containers from the view hierarchy
        containers.forEach { container ->
            try {
                (container.parent as? ViewGroup)?.removeView(container)
            } catch (e: Exception) {
                L.e("NativeMapHandler: Error removing map container during cleanup", e)
            }
        }
        
        // Clear the map to release references
        mapContainers.clear()
        pendingMapPlaceholders.clear()
        
        // Reset horizontal scroll state
        isHorizontalScrollInProgress = false
    }
}
