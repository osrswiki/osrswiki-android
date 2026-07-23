package com.omiyawaki.osrswiki.test

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.ui.map.AndroidMapPreloader
import com.omiyawaki.osrswiki.ui.map.PrototypeTerrainPreviewHost
import com.omiyawaki.osrswiki.ui.map.StandardNavigationMapFragment
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypePerformance
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypeTerrainCapture
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypeWindowFrameSample

class SemanticPrototypeStandaloneActivity : AppCompatActivity(), PrototypeTerrainPreviewHost {
    private var frameMetricsThread: HandlerThread? = null
    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_OSRSWiki_MapPrototype)
        suppressWindowTransitions()
        osrsMapPrototypePerformance.markPhase("activity_on_create_begin", "restored=${savedInstanceState != null}")
        osrsMapPrototypePerformance.measureCpuSpan(
            "activity_super_and_state_restore",
            "restored=${savedInstanceState != null}"
        ) {
            super.onCreate(savedInstanceState)
        }
        val persistedSession = if (
            savedInstanceState == null &&
            intent.getBooleanExtra(MapPrototypeStateStore.EXTRA_RESTORE_PERSISTED_STATE, false)
        ) {
            osrsMapPrototypePerformance.measureCpuSpan("persisted_session_load") {
                MapPrototypeStateStore.loadSession(this)
            }
        } else {
            null
        }
        val persistedFragmentState = persistedSession?.fragmentState
        val persistedTerrainPreview = persistedSession?.terrainPreview
        persistedSession?.let {
            osrsMapPrototypePerformance.markPhase(
                "persisted_session_resolved",
                "generation=${it.generation} preview_status=${it.previewStatus}"
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashView ->
                splashView.remove()
                osrsMapPrototypePerformance.markPhase("system_splash_removed_without_animation")
            }
        }
        installFrameMetricsListener()

        val container = osrsMapPrototypePerformance.measureCpuSpan("activity_container_inflation") {
            FrameLayout(this).apply {
                id = R.id.semantic_prototype_map_container
                val previewId = initialTerrainDrawableId()
                if (previewId != 0) setBackgroundResource(previewId)
                else setBackgroundColor(Color.rgb(23, 27, 23))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
        }
        osrsMapPrototypePerformance.measureCpuSpan("packaged_preview_install") {
            installInitialTerrainPreview(container, persistedTerrainPreview)
        }
        osrsMapPrototypePerformance.measureCpuSpan("activity_set_content_view") {
            setContentView(container)
        }
        osrsMapPrototypePerformance.markPhase("activity_content_installed")

        scheduleFirstContent(
            container,
            createFragment = savedInstanceState == null,
            persistedFragmentState = persistedFragmentState,
            terrainPreviewSource = if (persistedTerrainPreview != null) {
                "persisted_map_snapshot"
            } else {
                "packaged_raster_preview"
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        persistPrototypeState()
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        persistPrototypeState()
        super.onStop()
    }

    override fun onDestroy() {
        frameMetricsListener?.let(window::removeOnFrameMetricsAvailableListener)
        frameMetricsListener = null
        frameMetricsThread?.quitSafely()
        frameMetricsThread = null
        // Fragment teardown must detach listeners and the shared MapView before its native
        // resources are released. Releasing first races MapLibre's finalizer with the next
        // Activity created during a configuration change.
        super.onDestroy()
        // The prototype owns one application-context MapView for the process lifetime. Explicit
        // destruction between Activity hosts races MapLibre's native finalizer with the next map.
        AndroidMapPreloader.getInstance().retainForProcessLifetime()
    }

    fun mapFragmentForTesting(): StandardNavigationMapFragment? {
        return supportFragmentManager.findFragmentByTag(MAP_TAG) as? StandardNavigationMapFragment
    }

    override fun reservePrototypeTerrainGeneration(): Long {
        return MapPrototypeStateStore.reserveGeneration(applicationContext)
    }

    override fun onPrototypeTerrainPreview(
        capture: osrsMapPrototypeTerrainCapture,
        completion: (Boolean) -> Unit
    ) {
        MapPrototypeStateStore.enqueueTerrainPreview(
            context = applicationContext,
            capture = capture
        ) { result ->
            completion(result.success)
            osrsMapPrototypePerformance.markPhase(
                "persisted_terrain_preview_write_result",
                "generation=${result.generation} success=${result.success} reason=${result.reason}"
            )
        }
        osrsMapPrototypePerformance.markPhase(
            "persisted_terrain_preview_queued",
            "generation=${capture.generation} source_width=${capture.bitmap.width} " +
                "source_height=${capture.bitmap.height}"
        )
    }

    private fun installInitialTerrainPreview(container: FrameLayout, persistedPreview: Bitmap?) {
        val drawableId = initialTerrainDrawableId()
        if (persistedPreview == null && drawableId == 0) return
        container.addView(
            ImageView(this).apply {
                id = R.id.map_prototype_loading_preview
                if (persistedPreview != null) setImageBitmap(persistedPreview)
                else setImageResource(drawableId)
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                contentDescription = null
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun initialTerrainDrawableId(): Int {
        return resources.getIdentifier("map_prototype_initial_terrain", "drawable", packageName)
    }

    private fun suppressWindowTransitions() {
        window.setWindowAnimations(0)
        window.enterTransition = null
        window.exitTransition = null
        window.reenterTransition = null
        window.returnTransition = null
        window.sharedElementEnterTransition = null
        window.sharedElementExitTransition = null
        window.transitionBackgroundFadeDuration = 0L
        window.allowEnterTransitionOverlap = false
        window.allowReturnTransitionOverlap = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        osrsMapPrototypePerformance.markPhase("window_transitions_suppressed")
    }

    private fun scheduleFirstContent(
        container: FrameLayout,
        createFragment: Boolean,
        persistedFragmentState: Bundle?,
        terrainPreviewSource: String
    ) {
        container.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                container.viewTreeObserver.removeOnPreDrawListener(this)
                osrsMapPrototypePerformance.markForNextWindowFrame("first_app_content", "terrain_preview=true")
                osrsMapPrototypePerformance.markForNextWindowFrame(
                    "first_terrain_preview",
                    "source=$terrainPreviewSource"
                )
                if (!createFragment) {
                    osrsMapPrototypePerformance.markPhase("restored_fragment_first_content")
                    return true
                }
                container.post {
                    if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return@post
                    osrsMapPrototypePerformance.measureCpuSpan("fragment_transaction_enqueue") {
                        supportFragmentManager.beginTransaction()
                            .replace(
                                container.id,
                                StandardNavigationMapFragment.newInstance(
                                    lat = null,
                                    lon = null,
                                    zoom = null,
                                    plane = null,
                                    enableSemanticPrototype = true,
                                    restoredFragmentState = persistedFragmentState
                                ),
                                MAP_TAG
                            )
                            .commit()
                    }
                    osrsMapPrototypePerformance.markPhase("fragment_commit_enqueued")
                }
                return true
            }
        })
    }

    private fun persistPrototypeState() {
        val state = mapFragmentForTesting()?.prototypeHandoffState() ?: return
        val result = MapPrototypeStateStore.publishLifecycleState(this, state)
        osrsMapPrototypePerformance.markPhase(
            "persisted_lifecycle_generation",
            "generation=${result.generation} success=${result.success} reason=${result.reason}"
        )
    }

    private fun installFrameMetricsListener() {
        val thread = HandlerThread("map-prototype-window-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            fun metricMs(metric: Int): Double {
                return metrics.getMetric(metric).coerceAtLeast(0L) / 1_000_000.0
            }
            val deadlineMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                metricMs(FrameMetrics.DEADLINE)
            } else {
                16.667
            }
            osrsMapPrototypePerformance.recordWindowFrame(
                osrsMapPrototypeWindowFrameSample(
                    totalMs = metricMs(FrameMetrics.TOTAL_DURATION),
                    deadlineMs = deadlineMs,
                    inputHandlingMs = metricMs(FrameMetrics.INPUT_HANDLING_DURATION),
                    animationMs = metricMs(FrameMetrics.ANIMATION_DURATION),
                    layoutMeasureMs = metricMs(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    drawMs = metricMs(FrameMetrics.DRAW_DURATION),
                    syncMs = metricMs(FrameMetrics.SYNC_DURATION),
                    commandIssueMs = metricMs(FrameMetrics.COMMAND_ISSUE_DURATION),
                    swapBuffersMs = metricMs(FrameMetrics.SWAP_BUFFERS_DURATION)
                )
            )
        }
        frameMetricsThread = thread
        frameMetricsListener = listener
        window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
        osrsMapPrototypePerformance.markPhase("window_metrics_listener_attached")
    }

    companion object {
        private const val MAP_TAG = "semantic-prototype-standalone"
    }
}
