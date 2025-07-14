package com.omiyawaki.osrswiki.page

import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.JavascriptInterface
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.map.MapDiagnosticData
import com.omiyawaki.osrswiki.map.MapFragment
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

class NativeMapHandler(
    private val fragment: PageFragment,
    private val binding: FragmentPageBinding
) {
    val jsInterface = OsrsWikiBridge()

    /**
     * This flag is read by the PageFragment's gesture detector to avoid conflicts
     * with horizontal scrolling inside the WebView (e.g., on wide tables).
     * It is set to true by the `horizontal_scroll_interceptor.js` script.
     */
    @Volatile
    var isHorizontalScrollInProgress = false

    init {
        setupScrollListener()
    }

    inner class OsrsWikiBridge {
        /**
         * The bridge function for JavaScript to send log messages to Android's Logcat.
         */
        @JavascriptInterface
        fun log(message: String) {
            // Use a specific tag to easily filter these messages in Logcat.
            Log.d("OsrsWikiBridge", message)
        }

        @JavascriptInterface
        fun onInfoboxExpanded() {
            // This handler is now self-contained. It creates its own ViewTreeObserver
            // to reliably wait for the layout pass before calling back to JavaScript.
            binding.pageWebView.post {
                val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (binding.pageWebView.viewTreeObserver.isAlive) {
                            binding.pageWebView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        }
                        // The JS function `findAndShowNativeMap` is defined in `collapsible_content.js`.
                        val script = "javascript:findAndShowNativeMap(document.querySelector('.infobox'));"
                        binding.pageWebView.evaluateJavascript(script, null)
                    }
                }
                binding.pageWebView.viewTreeObserver.addOnGlobalLayoutListener(listener)
            }
        }

        @JavascriptInterface
        fun onMapFound(json: String) {
            fragment.lifecycleScope.launch {
                try {
                    val mapData = Json.decodeFromString<MapDiagnosticData>(json)
                    showNativeMap(mapData)
                } catch (e: Exception) {
                    L.e("Failed to parse map placeholder JSON", e)
                }
            }
        }

        @JavascriptInterface
        fun setMapVisibility(visible: Boolean) {
            fragment.view?.post {
                binding.mapView.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }

        /**
         * This method is called by `horizontal_scroll_interceptor.js`.
         */
        @JavascriptInterface
        fun setHorizontalScroll(inProgress: Boolean) {
            isHorizontalScrollInProgress = inProgress
        }
    }

    private fun showNativeMap(data: MapDiagnosticData) {
        if (!fragment.isAdded) return

        val container = binding.mapView
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
        container.visibility = View.VISIBLE

        if (fragment.childFragmentManager.findFragmentById(R.id.mapView) == null) {
            val mapFragment = MapFragment.newInstance(data)
            fragment.childFragmentManager.beginTransaction()
                .replace(R.id.mapView, mapFragment)
                .commit()
        }
    }

    private fun setupScrollListener() {
        binding.pageWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            binding.mapView.translationY = -scrollY.toFloat()
        }
    }
}
