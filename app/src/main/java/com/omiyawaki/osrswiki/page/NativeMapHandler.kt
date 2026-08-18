package com.omiyawaki.osrswiki.page

import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

private val osrsNativeMapJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class MapRect(val y: Float, val x: Float, val width: Float, val height: Float)

@Serializable
private data class MapData(
    val lat: String?,
    val lon: String?,
    val zoom: String?,
    val plane: String?,
    val mapId: Int? = null,
    val initiallyVisible: Boolean = false
)

private data class PendingMapPlaceholder(val rect: MapRect, val data: MapData)

class NativeMapHandler(
    private val fragment: PageFragment,
    private val binding: FragmentPageBinding
) {
    val jsInterface = OsrsWikiBridge()

    private val pendingMapPlaceholders = mutableMapOf<String, PendingMapPlaceholder>()
    private val mapContainers = mutableMapOf<String, View>()
    private val requestedVisibleMaps = mutableSetOf<String>()
    private val overlayState = ArticleNativeMapOverlayState()
    private val renderedMapIds = mutableSetOf<String>()
    private val offscreenTranslationX = -2000f // A value guaranteed to be off-screen
    private var isCleanedUp = false

    @Volatile
    var isHorizontalScrollInProgress = false
        private set

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
                    val rect = osrsNativeMapJson.decodeFromString<MapRect>(rectJson)
                    val mapData = osrsNativeMapJson.decodeFromString<MapData>(mapDataJson)
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
                // Record intent even when expansion happens before the DOM can report bounds.
                overlayState.recordDesiredVisibility(mapId, isOpening)
                if (isOpening) requestedVisibleMaps += mapId else requestedVisibleMaps -= mapId
                val view = if (isOpening) {
                    ensureMapContainer(mapId)
                } else {
                    mapContainers[mapId]
                } ?: return@post
                if (isOpening && renderedMapIds.contains(mapId)) {
                    view.translationX = 0f
                    hideStaticPlaceholder(mapId)
                } else if (!isOpening) {
                    view.translationX = offscreenTranslationX
                    showStaticPlaceholder(mapId)
                }
            }
        }

        @JavascriptInterface
        fun onMapViewportVisibilityChanged(mapId: String, isVisible: Boolean) {
            if (isCleanedUp) return
            fragment.view?.post {
                if (isCleanedUp) return@post
                // Intersection callbacks may also precede native overlay creation.
                overlayState.recordDesiredVisibility(mapId, isVisible)
                if (isVisible) {
                    requestedVisibleMaps += mapId
                    val view = ensureMapContainer(mapId)
                    if (view != null && renderedMapIds.contains(mapId)) {
                        view.translationX = 0f
                        hideStaticPlaceholder(mapId)
                    }
                } else {
                    requestedVisibleMaps -= mapId
                    mapContainers[mapId]?.translationX = offscreenTranslationX
                    showStaticPlaceholder(mapId)
                }
            }
        }

        @JavascriptInterface
        fun setHorizontalScroll(inProgress: Boolean) {
            if (isCleanedUp) {
                return
            }
            fragment.view?.post {
                updateHorizontalInteraction(inProgress, claimPointer = false)
            }
        }

        @JavascriptInterface
        fun setHorizontalScrollGesture(phase: String, gestureId: String, ownerId: String) {
            if (isCleanedUp) return
            val inProgress = phase == "begin" || phase == "change"
            val local = inProgress && ownerId != "article-navigation"
            val apply = Runnable {
                if (isCleanedUp) return@Runnable
                when {
                    local -> updateHorizontalInteraction(true, claimPointer = true)
                    phase == "begin" -> {
                        fragment.onArticleHorizontalScrollNotOwned()
                        updateHorizontalInteraction(false, claimPointer = false)
                    }
                    phase == "end" || phase == "cancel" -> {
                        fragment.onArticleHorizontalScrollReleased()
                        updateHorizontalInteraction(false, claimPointer = false)
                    }
                    else -> updateHorizontalInteraction(false, claimPointer = false)
                }
                L.d("NativeMapHandler: local gesture phase=$phase id=$gestureId owner=$ownerId local=$local")
            }
            Handler(Looper.getMainLooper()).postAtFrontOfQueue(apply)
        }

        @JavascriptInterface
        fun setArticleTouchSequence(sequence: Long) {
            if (isCleanedUp) return
            fragment.view?.post {
                if (!isCleanedUp) {
                    fragment.onArticleDomTouchSequence(sequence)
                }
            }
        }
        
        @JavascriptInterface
        fun log(message: String) {
            L.d("JS: $message")
        }

        @JavascriptInterface
        fun openFloorNumberingSettings() {
            if (isCleanedUp) return
            fragment.view?.post {
                if (!isCleanedUp) {
                    fragment.openFloorNumberingSettings()
                }
            }
        }

        @JavascriptInterface
        fun fetchText(url: String): String {
            val started = android.os.SystemClock.elapsedRealtime()
            return try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.setRequestProperty(
                    "User-Agent",
                    "osrswiki-android/1.7 (https://github.com/omiyawaki/osrswiki; native-bridge)"
                )
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                L.d("OsrsWikiBridge.fetchText $code in ${android.os.SystemClock.elapsedRealtime() - started}ms for $url")
                if (code in 200..299) body else ""
            } catch (error: Exception) {
                L.w("OsrsWikiBridge.fetchText failed after ${android.os.SystemClock.elapsedRealtime() - started}ms for $url: ${error.message}")
                ""
            }
        }
    }

    private fun rememberMapPlaceholder(id: String, rect: MapRect, data: MapData) {
        if (isCleanedUp) {
            return
        }
        pendingMapPlaceholders[id] = PendingMapPlaceholder(rect, data)
        val record = overlayState.recordMeasurement(
            id = id,
            bounds = rect.toArticleNativeMapBounds(),
            initiallyVisible = data.initiallyVisible
        )
        // Remeasure the actual overlay rather than only updating the next-create payload.
        mapContainers[id]?.let { applyMapContainerLayout(it, rect) }
        val shouldBeVisible = record.desiredVisible == true
        if (shouldBeVisible) {
            requestedVisibleMaps += id
            ensureMapContainer(id)
        } else {
            requestedVisibleMaps -= id
            mapContainers[id]?.translationX = offscreenTranslationX
            showStaticPlaceholder(id)
        }
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

        applyMapContainerLayout(container, rect)

        val mapFragment = CanonicalArticleMapFragment.newInstance(
            lat = data.lat,
            lon = data.lon,
            plane = data.plane,
            mapId = data.mapId?.toString()
        ).also { fragment ->
            fragment.onInteractionChanged = { inProgress ->
                updateHorizontalInteraction(inProgress, claimPointer = true)
            }
            fragment.onFirstFrame = {
                renderedMapIds += id
                if (requestedVisibleMaps.contains(id)) {
                    container.translationX = 0f
                    hideStaticPlaceholder(id)
                }
            }
            fragment.onFailure = { error ->
                L.e("NativeMapHandler: canonical article map failed for $id", error)
                showStaticPlaceholder(id)
                pendingMapPlaceholders.remove(id)
                removeMapContainer(id)
            }
        }
        fragment.childFragmentManager.beginTransaction()
            .replace(container.id, mapFragment)
            .commit()
        return container
    }

    /** Keep native overlays bound to live DOM cells after expansion, font load, or rotation. */
    private fun applyMapContainerLayout(container: View, rect: MapRect) {
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
        container.translationY = -binding.pageWebView.scrollY.toFloat()
    }

    private fun hideStaticPlaceholder(id: String) {
        binding.pageWebView.evaluateJavascript(
            "var el=document.getElementById('${id}'); if(el){el.style.opacity=0;}", null
        )
    }

    private fun showStaticPlaceholder(id: String) {
        binding.pageWebView.evaluateJavascript(
            "var el=document.getElementById('${id}'); if(el){el.style.opacity=1;}", null
        )
    }

    private fun updateHorizontalInteraction(inProgress: Boolean, claimPointer: Boolean) {
        if (isCleanedUp) return
        isHorizontalScrollInProgress = inProgress
        if (inProgress && claimPointer) {
            fragment.onArticleHorizontalScrollClaimed()
        }
    }

    private fun removeMapContainer(id: String) {
        renderedMapIds.remove(id)
        overlayState.remove(id)
        val container = mapContainers.remove(id) ?: return
        try {
            fragment.childFragmentManager.findFragmentById(container.id)?.let {
                fragment.childFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
            }
        } catch (error: Exception) {
            L.e("NativeMapHandler: failed to remove article map $id", error)
        }
        (container.parent as? ViewGroup)?.removeView(container)
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
        requestedVisibleMaps.clear()
        overlayState.clear()
        renderedMapIds.clear()
        
        // Reset horizontal scroll state
        isHorizontalScrollInProgress = false
    }

    private fun MapRect.toArticleNativeMapBounds() = ArticleNativeMapBounds(
        top = y,
        start = x,
        width = width,
        height = height
    )
}
