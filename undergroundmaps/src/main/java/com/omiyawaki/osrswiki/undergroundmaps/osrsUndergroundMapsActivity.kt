package com.omiyawaki.osrswiki.undergroundmaps

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.omiyawaki.osrswiki.undergroundmaps.data.osrsRealmRepository
import com.omiyawaki.osrswiki.undergroundmaps.data.osrsStagedRealmAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCatalog
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsEndpointZoomForViewport
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsMaximumDisplayExtentDp
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCameraEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmGroupLabel
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkTraversalDirection
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmRecord
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsCameraState
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsCameraPersistenceOwnership
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsInstalledCameraIdentity
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsRealmAction
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsRealmStateReducer
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsRealmStateStore
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsRealmUiState
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinkCatalogCache
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinkRow
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinksDialog
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinksDialogDebugState
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmPresentationCatalog
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmSelector
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmSelectorIndex
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsApplyRealmIdentityLayout
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmIdentityLayoutStateOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import java.util.ArrayDeque
import kotlin.math.ceil

class osrsUndergroundMapsActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var selectorButton: MaterialButton
    private lateinit var floorScroll: HorizontalScrollView
    private lateinit var linksButton: MaterialButton
    private lateinit var floorRow: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var topCard: MaterialCardView

    private lateinit var repository: osrsRealmRepository
    private lateinit var stateStore: osrsRealmStateStore
    private val reducer = osrsRealmStateReducer()
    private val cameraPersistenceOwnership = osrsCameraPersistenceOwnership()
    private var state: osrsRealmUiState? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var switchJob: Job? = null
    private var selectorDialog: AppCompatDialog? = null
    private var linksDialog: osrsRealmLinksDialog? = null
    private var realmPresentations: osrsRealmPresentationCatalog? = null
    private var realmLinkCatalogCache: osrsRealmLinkCatalogCache? = null
    private var realmSelectorIndex: osrsRealmSelectorIndex? = null
    private var styleGeneration = 0
    private var installedRequestId = Long.MIN_VALUE
    private var installedStyleGeneration = Int.MIN_VALUE
    private var activeSourceId: String? = null
    private var activeLayerId: String? = null
    private var stagedAssetSha256: String? = null
    private var stagedAssetPath: String? = null
    private var switchRequestedAtNanos: Long? = null
    private var switchCompletedAtNanos: Long? = null
    private var lastStageNanos: Long? = null
    private var lastRenderMarker: String = "not-rendered"
    private var lastError: String? = null
    private var lastSelectorFilterNanos: Long? = null
    private var lastLinkDialogOpenNanos: Long? = null
    private var coldLinkDialogOpenNanos: Long? = null
    private var lastLinkFilterNanos: Long? = null
    private var lastLinkFilterResultCount: Int? = null
    private var lastLinkDialogPhase: osrsLinkDialogOpenPhase? = null
    private var linkDialogOpenOrdinal = 0
    private var savedRealmId: String? = null
    private var savedPlane: Int? = null
    private var lastLinkNavigation: osrsLinkNavigationState? = null
    private var lastCameraPersistenceMarker: String = "not-attempted"
    private val switchDurationsNanos = ArrayDeque<Long>()
    private val simpleControlDurationsNanos = ArrayDeque<Long>()
    private val repeatedLinkDialogDurationsNanos = ArrayDeque<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        MapLibre.getInstance(applicationContext)
        repository = osrsRealmRepository(this)
        stateStore = osrsRealmStateStore(this)
        savedRealmId = savedInstanceState?.getString(OSRS_SAVED_REALM_ID)
        savedPlane = savedInstanceState?.takeIf { it.containsKey(OSRS_SAVED_PLANE) }
            ?.getInt(OSRS_SAVED_PLANE)

        buildContentView()
        mapView.onCreate(savedInstanceState)
        registerMapDiagnostics()
        initializeMapLibre()
        loadManifest()
    }

    private fun buildContentView() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.osrs_map_background))
        }
        mapView = MapView(this).apply {
            id = R.id.osrs_underground_map
            contentDescription = getString(R.string.map_content_description)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        root.addView(mapView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        selectorButton = MaterialButton(this).apply {
            id = R.id.osrs_realm_selector
            text = getString(R.string.loading_map)
            contentDescription = getString(R.string.realm_selector_description)
            isAllCaps = false
            osrsApplyRealmIdentityLayout()
            setOnClickListener { showRealmSelector() }
        }
        linksButton = MaterialButton(this).apply {
            id = R.id.osrs_realm_links
            text = getString(R.string.realm_links_unavailable)
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener { showRealmLinks() }
        }
        statusText = TextView(this).apply {
            id = R.id.osrs_map_status
            text = getString(R.string.reading_manifest)
            textSize = 11f
            setTextColor(getColor(R.color.osrs_ink))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(12), dp(4), dp(12), dp(8))
            contentDescription = text
        }
        progress = LinearProgressIndicator(this).apply {
            id = R.id.osrs_map_progress
            isIndeterminate = true
        }
        topCard = MaterialCardView(this).apply {
            radius = dp(10).toFloat()
            cardElevation = dp(4).toFloat()
            setCardBackgroundColor(getColor(R.color.osrs_parchment))
            addView(LinearLayout(this@osrsUndergroundMapsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@osrsUndergroundMapsActivity).apply {
                    text = getString(R.string.app_name)
                    textSize = 13f
                    setTextColor(getColor(R.color.osrs_ink))
                    setPadding(dp(14), dp(8), dp(14), 0)
                }, matchWrap())
                addView(selectorButton, matchWrap())
                addView(linksButton, matchWrap())
                addView(this@osrsUndergroundMapsActivity.progress, matchWrap())
                addView(statusText, matchWrap())
            })
        }
        root.addView(topCard, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
            topMargin = dp(12)
        })

        floorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        floorScroll = HorizontalScrollView(this).apply {
            id = R.id.osrs_floor_controls
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.argb(224, 33, 23, 14))
            addView(floorRow)
            visibility = View.GONE
            contentDescription = "Floor controls"
        }
        root.addView(floorScroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = dp(12) })

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (topCard.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = bars.top + dp(12)
                leftMargin = bars.left + dp(12)
                rightMargin = bars.right + dp(12)
                topCard.layoutParams = this
            }
            (floorScroll.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = bars.left + dp(12)
                rightMargin = bars.right + dp(12)
                bottomMargin = bars.bottom + dp(12)
                floorScroll.layoutParams = this
            }
            insets
        }
        setContentView(root)
    }

    private fun initializeMapLibre() {
        mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = true
                setCompassGravity(Gravity.BOTTOM or Gravity.END)
                setCompassMargins(0, 0, dp(12), dp(72))
            }
            loadBaseStyle(mapLibreMap)
            mapLibreMap.addOnCameraIdleListener {
                persistCurrentCamera()
                lastRenderMarker = "camera-idle@${SystemClock.elapsedRealtimeNanos()}"
                renderDiagnostics()
            }
        }
    }

    private fun loadBaseStyle(mapLibreMap: MapLibreMap) {
        cameraPersistenceOwnership.clear()
        mapLibreMap.setStyle(Style.Builder().fromJson(OSRS_BASE_STYLE_JSON)) { loadedStyle ->
            styleGeneration += 1
            style = loadedStyle
            activeSourceId = null
            activeLayerId = null
            state = state?.let { reducer.reduce(it, osrsRealmAction.StyleReloaded) }
            switchRequestedAtNanos = SystemClock.elapsedRealtimeNanos()
            switchCompletedAtNanos = null
            attemptInstallActiveAsset()
        }
    }

    private fun loadManifest() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                initializeCatalog(repository.loadCatalog())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                showFailure("Manifest rejected", failure)
            }
        }
    }

    private fun initializeCatalog(catalog: osrsRealmCatalog) {
        realmPresentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        realmLinkCatalogCache = osrsRealmLinkCatalogCache(catalog, realmPresentations!!)
        realmSelectorIndex = osrsRealmSelectorIndex(catalog.sections, realmPresentations!!)
        state = reducer.initial(
            catalog = catalog,
            persisted = stateStore.load(),
            restoredRealmId = savedRealmId,
            restoredPlane = savedPlane
        )
        check(catalog.realmCount == catalog.selectorCount) {
            "Selector and manifest counts differ"
        }
        updateControls()
        stateStore.save(state!!)
        switchRequestedAtNanos = SystemClock.elapsedRealtimeNanos()
        switchCompletedAtNanos = null
        attemptInstallActiveAsset()
    }

    private fun showRealmSelector() {
        val current = state ?: return
        val selectorIndex = realmSelectorIndex ?: return
        val started = SystemClock.elapsedRealtimeNanos()
        selectorDialog?.dismiss()
        selectorDialog = osrsRealmSelector(
            context = this,
            selectorIndex = selectorIndex,
            activeRealmId = current.activeRealmId,
            onRealmSelected = ::selectRealm,
            onFilterMeasured = { query, resultCount, elapsed ->
                lastSelectorFilterNanos = elapsed
                recordSimpleControl(elapsed)
                Log.d(OSRS_LOG_TAG, "selector_filter queryLength=${query.length} results=$resultCount appNanos=$elapsed")
                renderDiagnostics()
            }
        ).show()
        recordSimpleControl(SystemClock.elapsedRealtimeNanos() - started)
    }

    private fun selectRealm(realm: osrsRealmRecord) {
        val current = state ?: return
        if (realm.id == current.activeRealmId) return
        val started = SystemClock.elapsedRealtimeNanos()
        persistCurrentCamera()
        state = reducer.reduce(state!!, osrsRealmAction.SelectRealm(realm.id))
        stateStore.save(state!!)
        switchRequestedAtNanos = SystemClock.elapsedRealtimeNanos()
        switchCompletedAtNanos = null
        lastError = null
        updateControls()
        attemptInstallActiveAsset()
        recordSimpleControl(SystemClock.elapsedRealtimeNanos() - started)
    }

    private fun selectPlane(plane: Int) {
        val current = state ?: return
        if (plane == current.activePlane || plane !in current.activeRealm.planes) return
        val started = SystemClock.elapsedRealtimeNanos()
        persistCurrentCamera()
        state = reducer.reduce(state!!, osrsRealmAction.SelectPlane(plane))
        stateStore.save(state!!)
        switchRequestedAtNanos = SystemClock.elapsedRealtimeNanos()
        switchCompletedAtNanos = null
        lastError = null
        updateControls()
        attemptInstallActiveAsset()
        recordSimpleControl(SystemClock.elapsedRealtimeNanos() - started)
    }

    private fun showRealmLinks() {
        val current = state ?: return
        val catalogCache = realmLinkCatalogCache ?: return
        val started = SystemClock.elapsedRealtimeNanos()
        val catalogStarted = SystemClock.elapsedRealtimeNanos()
        val linkCatalogLookup = catalogCache.get(current.activeRealm)
        val catalogNanos = SystemClock.elapsedRealtimeNanos() - catalogStarted
        val linkCatalog = linkCatalogLookup.catalog

        val priorDialogCleanupStarted = SystemClock.elapsedRealtimeNanos()
        val dialogReused = linksDialog?.realmId == current.activeRealmId
        if (!dialogReused) {
            linksDialog?.dismiss()
            linksDialog = null
        }
        val priorDialogCleanupNanos =
            SystemClock.elapsedRealtimeNanos() - priorDialogCleanupStarted

        val controller = linksDialog ?: osrsRealmLinksDialog(
            context = this,
            links = linkCatalog,
            onLinkSelected = { row -> selectAuthoritativeLink(row) },
            onFilterMeasured = { query, resultCount, elapsed ->
                lastLinkFilterNanos = elapsed
                lastLinkFilterResultCount = resultCount
                recordSimpleControl(elapsed)
                Log.d(
                    OSRS_LOG_TAG,
                    "link_filter queryLength=${query.length} results=$resultCount appNanos=$elapsed"
                )
                renderDiagnostics()
            }
        ).also { linksDialog = it }
        val showResult = controller.show()
        val appNanos = SystemClock.elapsedRealtimeNanos() - started
        val classifiedNanos = catalogNanos +
            priorDialogCleanupNanos +
            showResult.viewConstructionNanos +
            showResult.initialFilterNanos +
            showResult.initialRowConversionNanos +
            showResult.initialAdapterSubmissionNanos +
            showResult.materialShowNanos
        val unclassifiedNanos = appNanos - classifiedNanos
        check(unclassifiedNanos >= 0L) { "Link dialog phase accounting exceeded total time" }
        linkDialogOpenOrdinal += 1
        lastLinkDialogPhase = osrsLinkDialogOpenPhase(
            ordinal = linkDialogOpenOrdinal,
            cold = linkDialogOpenOrdinal == 1,
            realmId = current.activeRealmId,
            availableCount = linkCatalog.availableRows.size,
            unavailableCount = linkCatalog.unavailableCount,
            catalogCacheHit = linkCatalogLookup.cacheHit,
            catalogNanos = catalogNanos,
            catalogBuildNanos = if (linkCatalogLookup.cacheHit) 0L else catalogNanos,
            priorDialogCleanupNanos = priorDialogCleanupNanos,
            dialogReused = dialogReused,
            viewConstructionNanos = showResult.viewConstructionNanos,
            initialFilterNanos = showResult.initialFilterNanos,
            initialRowConversionNanos = showResult.initialRowConversionNanos,
            initialAdapterSubmissionNanos = showResult.initialAdapterSubmissionNanos,
            initialUpdateStrategy = showResult.initialUpdateStrategy,
            initialFilterObserverNanos = 0L,
            materialShowNanos = showResult.materialShowNanos,
            unclassifiedNanos = unclassifiedNanos,
            appNanos = appNanos,
            showingAfterReturn = showResult.showingAfterReturn
        )
        lastLinkDialogOpenNanos = appNanos
        val phase = requireNotNull(lastLinkDialogPhase)
        if (phase.cold) {
            coldLinkDialogOpenNanos = appNanos
        } else {
            repeatedLinkDialogDurationsNanos.addLast(appNanos)
            while (repeatedLinkDialogDurationsNanos.size > OSRS_PERFORMANCE_SAMPLE_LIMIT) {
                repeatedLinkDialogDurationsNanos.removeFirst()
            }
            recordSimpleControl(appNanos)
        }
        Log.d(
            OSRS_LOG_TAG,
            "link_dialog_phase candidate=${current.catalog.manifest.candidate} " +
                "ordinal=${phase.ordinal} cold=${phase.cold} realmId=${phase.realmId} " +
                "available=${phase.availableCount} unavailable=${phase.unavailableCount} " +
                "catalogCacheHit=${phase.catalogCacheHit} catalogNanos=${phase.catalogNanos} " +
                "catalogBuildNanos=${phase.catalogBuildNanos} " +
                "priorDialogCleanupNanos=${phase.priorDialogCleanupNanos} " +
                "dialogReused=${phase.dialogReused} " +
                "viewConstructionNanos=${phase.viewConstructionNanos} " +
                "initialFilterNanos=${phase.initialFilterNanos} " +
                "initialRowConversionNanos=${phase.initialRowConversionNanos} " +
                "initialAdapterSubmissionNanos=${phase.initialAdapterSubmissionNanos} " +
                "initialUpdateStrategy=${phase.initialUpdateStrategy} " +
                "initialFilterObserverNanos=${phase.initialFilterObserverNanos} " +
                "materialShowNanos=${phase.materialShowNanos} " +
                "unclassifiedNanos=${phase.unclassifiedNanos} appNanos=${phase.appNanos} " +
                "showingAfterReturn=${phase.showingAfterReturn}"
        )
        Log.d(
            OSRS_LOG_TAG,
            "link_dialog_open available=${linkCatalog.availableRows.size} " +
                "unavailable=${linkCatalog.unavailableCount} appNanos=$appNanos"
        )
        renderDiagnostics()
    }

    private fun selectAuthoritativeLink(
        row: osrsRealmLinkRow,
        maximumViewportExtentDpOverride: Double? = null
    ) {
        val current = state ?: return
        val link = row.link
        if (!link.authoritative || link.availability != "available") return
        val targetId = row.targetRealm.id
        val targetPlane = row.targetPosition.plane
        if (targetPlane !in row.targetRealm.planes) return
        val started = SystemClock.elapsedRealtimeNanos()
        persistCurrentCamera()
        var next = reducer.reduce(state!!, osrsRealmAction.SelectRealm(targetId))
        next = reducer.reduce(next, osrsRealmAction.SelectPlane(targetPlane))
        val maximumViewportExtentDp = maximumViewportExtentDpOverride
            ?: maximumDisplayExtentDp()
        val destinationZoom = osrsEndpointZoomForViewport(
            row.targetRealm,
            row.destination,
            maximumViewportExtentDp
        )
        val destinationCamera = osrsCameraState(
            latitude = row.destination.latitude,
            longitude = row.destination.longitude,
            zoom = destinationZoom
        )
        next = reducer.reduce(next, osrsRealmAction.CameraChanged(destinationCamera))
        state = next
        lastLinkNavigation = osrsLinkNavigationState(
            linkId = link.id,
            linkSideKey = row.side.key,
            traversalDirection = row.side.traversalDirection,
            targetRealmId = targetId,
            targetPlane = targetPlane,
            targetGameX = row.targetPosition.x,
            targetGameY = row.targetPosition.y,
            mappedLatitude = row.destination.latitude,
            mappedLongitude = row.destination.longitude,
            mappedZoom = destinationZoom,
            matchingLayoutCount = row.destination.matchingLayoutCount,
            appliedMarker = "requested@${SystemClock.elapsedRealtimeNanos()}"
        )
        stateStore.save(next)
        switchRequestedAtNanos = SystemClock.elapsedRealtimeNanos()
        switchCompletedAtNanos = null
        lastError = null
        updateControls()
        if (next.switchRequestId == current.switchRequestId) {
            applyCamera(destinationCamera)
            markLinkCameraApplied(targetId, targetPlane, "current-style")
            switchCompletedAtNanos = SystemClock.elapsedRealtimeNanos()
            recordSwitchDuration(switchCompletedAtNanos!! - switchRequestedAtNanos!!)
            lastRenderMarker = "link-camera-applied-awaiting-render"
            renderDiagnostics()
        } else {
            attemptInstallActiveAsset()
        }
        recordSimpleControl(SystemClock.elapsedRealtimeNanos() - started)
    }

    private fun attemptInstallActiveAsset() {
        val current = state ?: return
        val loadedStyle = style ?: return
        if (installedRequestId == current.switchRequestId && installedStyleGeneration == styleGeneration) return
        val requestId = current.switchRequestId
        val generation = styleGeneration
        val realm = current.activeRealm
        val asset = current.activeAsset
        progress.visibility = View.VISIBLE
        selectorButton.isEnabled = true
        switchJob?.cancel()
        switchJob = lifecycleScope.launch {
            try {
                val staged = repository.stage(asset)
                val latest = state
                if (latest?.switchRequestId != requestId || styleGeneration != generation || style !== loadedStyle) {
                    return@launch
                }
                installRaster(loadedStyle, realm, asset, staged, requestId, generation)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                val latest = state
                if (latest?.switchRequestId == requestId && styleGeneration == generation) {
                    showFailure("Could not open ${realm.canonicalName}", failure)
                }
            }
        }
    }

    private fun installRaster(
        loadedStyle: Style,
        realm: osrsRealmRecord,
        asset: osrsRealmAsset,
        staged: osrsStagedRealmAsset,
        requestId: Long,
        generation: Int
    ) {
        val appStarted = switchRequestedAtNanos ?: SystemClock.elapsedRealtimeNanos()
        val sourceId = "osrs-realm-source-$generation-$requestId"
        val layerId = "osrs-realm-layer-$generation-$requestId"
        val oldLayer = activeLayerId
        val oldSource = activeSourceId

        if (loadedStyle.getSource(sourceId) == null) {
            loadedStyle.addSource(
                RasterSource(sourceId, "mbtiles://${staged.file.absolutePath}", asset.tileSize)
            )
        }
        if (loadedStyle.getLayer(layerId) == null) {
            loadedStyle.addLayer(
                RasterLayer(layerId, sourceId).withProperties(
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.rasterOpacity(1.0f),
                    PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_NEAREST),
                    PropertyFactory.rasterFadeDuration(0f)
                )
            )
        }

        activeLayerId = layerId
        activeSourceId = sourceId
        if (oldLayer != null && oldLayer != layerId && loadedStyle.getLayer(oldLayer) != null) {
            loadedStyle.removeLayer(oldLayer)
        }
        if (oldSource != null && oldSource != sourceId && loadedStyle.getSource(oldSource) != null) {
            loadedStyle.removeSource(oldSource)
        }

        installedRequestId = requestId
        installedStyleGeneration = generation
        stagedAssetSha256 = staged.sha256
        stagedAssetPath = asset.mbtilesPath
        lastStageNanos = staged.elapsedNanos
        configureRealmCamera(
            realm,
            asset,
            osrsInstalledCameraIdentity(
                realmId = realm.id,
                plane = asset.plane,
                requestId = requestId,
                styleGeneration = generation
            )
        )
        switchCompletedAtNanos = SystemClock.elapsedRealtimeNanos()
        val duration = switchCompletedAtNanos!! - appStarted
        recordSwitchDuration(duration)
        progress.visibility = View.GONE
        lastError = null
        lastRenderMarker = "installed-awaiting-render"
        map?.triggerRepaint()
        updateControls()
        Log.i(
            OSRS_LOG_TAG,
            "realm_switch id=${realm.id} plane=${asset.plane} source=$sourceId appNanos=$duration " +
                "stageNanos=${staged.elapsedNanos} reused=${staged.reusedVerifiedCopy} sha256=${staged.sha256}"
        )
    }

    private fun configureRealmCamera(
        realm: osrsRealmRecord,
        asset: osrsRealmAsset,
        identity: osrsInstalledCameraIdentity
    ) {
        val mapLibreMap = map ?: return
        val bounds = boundsFor(asset)
        // Keep the true surface constrained so it cannot be panned toward the removed atlas.
        // Modular realms contain only their own pixels, and some authoritative endpoints sit
        // close enough to a content edge that a strict target bound would move the requested
        // camera center. Clear the surface constraint before applying those exact endpoints.
        val cameraTargetBounds = bounds.takeIf { realm.isSurface }
        mapLibreMap.setLatLngBoundsForCameraTarget(cameraTargetBounds)
        mapLibreMap.setMinZoomPreference(osrsRealmCameraEnvelope.minZoom(asset))
        mapLibreMap.setMaxZoomPreference(osrsRealmCameraEnvelope.maxZoom(asset))
        val remembered = state?.cameras?.get(realm.id)?.takeIf { it.isWithin(asset) }
        if (remembered != null) {
            val latest = state ?: return
            if (!cameraPersistenceOwnership.markInstalled(identity, latest, styleGeneration)) return
            applyCamera(remembered)
            markLinkCameraApplied(realm.id, asset.plane, "installed-style")
        } else {
            mapView.post {
                val latest = state ?: return@post
                if (
                    installedRequestId == identity.requestId &&
                    installedStyleGeneration == identity.styleGeneration &&
                    cameraPersistenceOwnership.markInstalled(
                        identity,
                        latest,
                        styleGeneration
                    )
                ) {
                    mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(48)))
                }
            }
        }
    }

    private fun applyCamera(camera: osrsCameraState) {
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(camera.latitude, camera.longitude))
            .zoom(camera.zoom)
            .bearing(camera.bearing)
            .tilt(camera.tilt)
            .build()
    }

    private fun maximumDisplayExtentDp(): Double {
        val metrics: DisplayMetrics
        val widthPixels: Int
        val heightPixels: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            metrics = resources.displayMetrics
            val bounds = windowManager.maximumWindowMetrics.bounds
            widthPixels = bounds.width()
            heightPixels = bounds.height()
        } else {
            metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            widthPixels = metrics.widthPixels
            heightPixels = metrics.heightPixels
        }
        return osrsMaximumDisplayExtentDp(
            widthPixels = widthPixels,
            heightPixels = heightPixels,
            density = metrics.density.toDouble(),
            densityDpi = metrics.densityDpi
        )
    }

    private fun markLinkCameraApplied(realmId: String, plane: Int, phase: String) {
        val navigation = lastLinkNavigation ?: return
        if (navigation.targetRealmId != realmId || navigation.targetPlane != plane) return
        lastLinkNavigation = navigation.copy(
            appliedMarker = "camera-applied-$phase@${SystemClock.elapsedRealtimeNanos()}"
        )
    }

    private fun persistCurrentCamera() {
        val currentState = state ?: return
        val installedIdentity = cameraPersistenceOwnership.authorization(
            currentState,
            styleGeneration
        ) ?: run {
            lastCameraPersistenceMarker =
                "skipped-unowned:${currentState.activeRealmId}:${currentState.activePlane}:" +
                    "${currentState.switchRequestId}:$styleGeneration"
            return
        }
        val camera = map?.cameraPosition ?: return
        val target = camera.target ?: return
        val snapshot = osrsCameraState(
            latitude = target.latitude,
            longitude = target.longitude,
            zoom = camera.zoom,
            bearing = camera.bearing,
            tilt = camera.tilt
        )
        if (!snapshot.isWithin(currentState.activeAsset)) return
        state = reducer.reduce(
            currentState,
            osrsRealmAction.InstalledCameraChanged(
                realmId = installedIdentity.realmId,
                plane = installedIdentity.plane,
                requestId = installedIdentity.requestId,
                camera = snapshot
            )
        )
        lastCameraPersistenceMarker =
            "persisted:${installedIdentity.realmId}:${installedIdentity.plane}:" +
                "${installedIdentity.requestId}:${installedIdentity.styleGeneration}"
        stateStore.save(state!!)
    }

    private fun updateControls() {
        val current = state ?: return
        val realm = current.activeRealm
        val presentation = realmPresentations?.get(realm)
        val visibleRealmName = presentation?.visibleName ?: realm.canonicalName
        val accessibleRealmName = presentation?.accessibilityName ?: realm.canonicalName
        selectorButton.text = visibleRealmName
        selectorButton.contentDescription =
            "${getString(R.string.realm_selector_description)}. Current: $accessibleRealmName, ${osrsRealmGroupLabel(realm.group)}"
        val availableLinkCount = realm.links
            .asSequence()
            .filter { it.authoritative && it.availability == "available" }
            .sumOf { it.endpointSidesFor(realm.id).size }
        val unavailableLinkCount = realm.links.count {
            !it.authoritative || it.availability != "available"
        }
        linksButton.visibility = if (realm.links.isEmpty()) View.GONE else View.VISIBLE
        linksButton.text = if (availableLinkCount > 0 && unavailableLinkCount > 0) {
            getString(R.string.realm_links_mixed, availableLinkCount, unavailableLinkCount)
        } else if (availableLinkCount > 0) {
            getString(R.string.realm_links_available, availableLinkCount)
        } else {
            getString(R.string.realm_links_unavailable)
        }
        linksButton.contentDescription = getString(R.string.realm_links_description, accessibleRealmName)
        floorRow.removeAllViews()
        if (realm.planes.size > 1) {
            floorScroll.visibility = View.VISIBLE
            val checkedState = intArrayOf(android.R.attr.state_checked)
            val defaultState = intArrayOf()
            val floorTextColors = ColorStateList(
                arrayOf(checkedState, defaultState),
                intArrayOf(getColor(R.color.osrs_ink), getColor(R.color.osrs_parchment))
            )
            val floorBackgroundColors = ColorStateList(
                arrayOf(checkedState, defaultState),
                intArrayOf(getColor(R.color.osrs_parchment), Color.TRANSPARENT)
            )
            val floorStrokeColor = ColorStateList.valueOf(getColor(R.color.osrs_parchment))
            realm.planes.sorted().forEach { plane ->
                floorRow.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = getString(R.string.floor_label, plane)
                    textSize = 13f
                    isAllCaps = false
                    isCheckable = true
                    setTextColor(floorTextColors)
                    backgroundTintList = floorBackgroundColors
                    strokeColor = floorStrokeColor
                    strokeWidth = dp(1)
                    isChecked = plane == current.activePlane
                    minHeight = dp(48)
                    contentDescription = getString(R.string.floor_button_description, plane, accessibleRealmName)
                    setOnClickListener { selectPlane(plane) }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(4) })
            }
        } else {
            floorScroll.visibility = View.GONE
        }
        renderDiagnostics()
    }

    private fun registerMapDiagnostics() {
        mapView.addOnDidFinishRenderingMapListener { fully ->
            if (fully) {
                lastRenderMarker = "map-fully-rendered@${SystemClock.elapsedRealtimeNanos()}"
                renderDiagnostics()
            }
        }
        mapView.addOnDidBecomeIdleListener {
            lastRenderMarker = "map-idle@${SystemClock.elapsedRealtimeNanos()}"
            renderDiagnostics()
        }
        mapView.addOnDidFailLoadingMapListener { message ->
            lastError = message
            renderDiagnostics()
            Log.e(OSRS_LOG_TAG, "map_load_failed message=$message")
        }
    }

    private fun renderDiagnostics() {
        val diagnostics = debugStateForTesting()
        statusText.text = when {
            diagnostics.error != null -> diagnostics.error
            diagnostics.activeRealmId == null -> getString(R.string.reading_manifest)
            diagnostics.switchCompletedAtNanos == null ->
                "${diagnostics.activeRealmDisplayName} • ${diagnostics.activeGroupLabel} • " +
                    "floor ${diagnostics.activePlane} • staging verified asset"
            else -> buildString {
                append(diagnostics.activeRealmDisplayName)
                append(" • ")
                append(diagnostics.activeGroupLabel)
                append(" • floor ")
                append(diagnostics.activePlane)
                append(" • switch ")
                append(formatMillis(diagnostics.lastAppOwnedSwitchNanos))
                append(" ms")
                diagnostics.switchP95Nanos?.let {
                    append(" • p95 ")
                    append(formatMillis(it))
                    append(" ms")
                }
            }
        }
        statusText.contentDescription = diagnostics.toString()
    }

    private fun showFailure(prefix: String, failure: Throwable) {
        progress.visibility = View.GONE
        lastError = "$prefix: ${failure.message ?: failure.javaClass.simpleName}"
        Log.e(OSRS_LOG_TAG, lastError, failure)
        renderDiagnostics()
    }

    fun reloadStyleForTesting(): Boolean {
        val mapLibreMap = map ?: return false
        persistCurrentCamera()
        loadBaseStyle(mapLibreMap)
        return true
    }

    fun selectRealmForTesting(realmId: String): Boolean {
        val realm = state?.catalog?.byId?.get(realmId) ?: return false
        selectRealm(realm)
        return true
    }

    fun openRealmSelectorForTesting(): Boolean {
        if (state == null || realmSelectorIndex == null) return false
        showRealmSelector()
        return selectorDialog?.isShowing == true
    }

    fun filterRealmSelectorForTesting(query: String): Boolean {
        val search = selectorDialog?.findViewById<EditText>(R.id.osrs_selector_search)
            ?: return false
        search.setText(query)
        search.setSelection(search.text.length)
        return true
    }

    fun dismissRealmSelectorForTesting() {
        selectorDialog?.dismiss()
    }

    fun realmLinksDialogStateForTesting(): osrsRealmLinksDialogDebugState? =
        linksDialog?.debugState()

    fun lastLinkDialogPhaseForTesting(): osrsLinkDialogOpenPhase? = lastLinkDialogPhase

    fun selectPlaneForTesting(plane: Int): Boolean {
        val current = state ?: return false
        if (plane !in current.activeRealm.planes) return false
        selectPlane(plane)
        return true
    }

    fun selectAuthoritativeLinkForTesting(
        linkId: String,
        traversalDirection: osrsRealmLinkTraversalDirection? = null,
        maximumViewportExtentDp: Double? = null
    ): Boolean {
        val current = state ?: return false
        val catalogCache = realmLinkCatalogCache ?: return false
        val candidates = catalogCache.get(current.activeRealm).catalog
            .availableRows
            .filter { it.link.id == linkId }
        val row = if (traversalDirection == null) {
            candidates.singleOrNull()
        } else {
            candidates.singleOrNull { it.side.traversalDirection == traversalDirection }
        } ?: return false
        selectAuthoritativeLink(row, maximumViewportExtentDp)
        return true
    }

    fun debugStateForTesting(): osrsMapDiagnostics {
        val current = state
        val camera = map?.cameraPosition
        val asset = current?.activeAsset
        val identityLayout = selectorButton.osrsRealmIdentityLayoutStateOrNull()
        return osrsMapDiagnostics(
            candidate = current?.catalog?.manifest?.candidate,
            activeRealmId = current?.activeRealmId,
            activeRealmName = current?.activeRealm?.canonicalName,
            activeRealmDisplayName = current?.activeRealm?.let { realm ->
                realmPresentations?.get(realm)?.visibleName ?: realm.canonicalName
            },
            activeGroup = current?.activeRealm?.group,
            activeGroupLabel = current?.activeRealm?.group?.let(::osrsRealmGroupLabel),
            activePlane = current?.activePlane,
            activeSwitchRequestId = current?.switchRequestId,
            sourceId = activeSourceId,
            layerId = activeLayerId,
            stagedAssetSha256 = stagedAssetSha256,
            stagedAssetPath = stagedAssetPath,
            realmBounds = asset?.contentLatlonBounds,
            cameraLatitude = camera?.target?.latitude,
            cameraLongitude = camera?.target?.longitude,
            cameraZoom = camera?.zoom,
            cameraBearing = camera?.bearing,
            styleGeneration = styleGeneration,
            installedCameraRealmId = cameraPersistenceOwnership.installedIdentity?.realmId,
            installedCameraPlane = cameraPersistenceOwnership.installedIdentity?.plane,
            installedCameraRequestId = cameraPersistenceOwnership.installedIdentity?.requestId,
            installedCameraStyleGeneration = cameraPersistenceOwnership.installedIdentity?.styleGeneration,
            cameraPersistenceMarker = lastCameraPersistenceMarker,
            renderMarker = lastRenderMarker,
            switchRequestedAtNanos = switchRequestedAtNanos,
            switchCompletedAtNanos = switchCompletedAtNanos,
            lastAppOwnedSwitchNanos = switchRequestedAtNanos?.let { start ->
                switchCompletedAtNanos?.minus(start)
            },
            lastStageNanos = lastStageNanos,
            switchP95Nanos = percentile95(switchDurationsNanos),
            simpleControlP95Nanos = percentile95(simpleControlDurationsNanos),
            lastSelectorFilterNanos = lastSelectorFilterNanos,
            lastLinkDialogOpenNanos = lastLinkDialogOpenNanos,
            coldLinkDialogOpenNanos = coldLinkDialogOpenNanos,
            repeatedLinkDialogP95Nanos = percentile95(repeatedLinkDialogDurationsNanos),
            lastLinkFilterNanos = lastLinkFilterNanos,
            lastLinkFilterResultCount = lastLinkFilterResultCount,
            fontScale = resources.configuration.fontScale,
            screenWidthDp = resources.configuration.screenWidthDp,
            screenHeightDp = resources.configuration.screenHeightDp,
            selectorIdentityAccessibilityText = selectorButton.contentDescription?.toString(),
            selectorIdentityTextLength = identityLayout?.textLength,
            selectorIdentityLineCount = identityLayout?.lineCount,
            selectorIdentityMaxLines = selectorButton.maxLines,
            selectorIdentityLastVisibleEnd = identityLayout?.lastVisibleEnd,
            selectorIdentityEllipsisCount = identityLayout?.ellipsisCount,
            selectorIdentityHonest = identityLayout?.honest,
            selectorIdentityWidthPx = selectorButton.width,
            selectorIdentityHeightPx = selectorButton.height,
            topAndFloorControlsSeparated = if (
                floorScroll.visibility == View.VISIBLE &&
                topCard.bottom > 0 &&
                floorScroll.top > 0
            ) {
                topCard.bottom <= floorScroll.top
            } else {
                null
            },
            selectorAndStatusSeparated = if (statusText.top > 0) {
                selectorButton.bottom <= statusText.top
            } else {
                null
            },
            manifestRealmCount = current?.catalog?.realmCount,
            selectorRealmCount = current?.catalog?.selectorCount,
            manifestAssetNonblank = asset?.nonblank,
            availableLinkCount = current?.activeRealm?.let { realm ->
                realm.links
                    .asSequence()
                    .filter { it.authoritative && it.availability == "available" }
                    .sumOf { it.endpointSidesFor(realm.id).size }
            },
            unavailableLinkCount = current?.activeRealm?.links?.count {
                !it.authoritative || it.availability != "available"
            },
            selectedLinkId = lastLinkNavigation?.linkId,
            selectedLinkSideKey = lastLinkNavigation?.linkSideKey,
            selectedLinkTraversalDirection = lastLinkNavigation?.traversalDirection?.name,
            linkTargetRealmId = lastLinkNavigation?.targetRealmId,
            linkTargetPlane = lastLinkNavigation?.targetPlane,
            linkTargetGameX = lastLinkNavigation?.targetGameX,
            linkTargetGameY = lastLinkNavigation?.targetGameY,
            linkMappedLatitude = lastLinkNavigation?.mappedLatitude,
            linkMappedLongitude = lastLinkNavigation?.mappedLongitude,
            linkMappedZoom = lastLinkNavigation?.mappedZoom,
            linkMatchingLayoutCount = lastLinkNavigation?.matchingLayoutCount,
            linkAppliedMarker = lastLinkNavigation?.appliedMarker,
            error = lastError
        )
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        persistCurrentCamera()
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        selectorDialog?.dismiss()
        linksDialog?.dismiss()
        switchJob?.cancel()
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        persistCurrentCamera()
        state?.let {
            outState.putString(OSRS_SAVED_REALM_ID, it.activeRealmId)
            outState.putInt(OSRS_SAVED_PLANE, it.activePlane)
        }
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    private fun boundsFor(asset: osrsRealmAsset): LatLngBounds {
        return LatLngBounds.Builder()
            .include(LatLng(asset.south, asset.west))
            .include(LatLng(asset.north, asset.east))
            .build()
    }

    private fun recordSwitchDuration(nanos: Long) {
        switchDurationsNanos.addLast(nanos)
        while (switchDurationsNanos.size > OSRS_PERFORMANCE_SAMPLE_LIMIT) switchDurationsNanos.removeFirst()
    }

    private fun recordSimpleControl(nanos: Long) {
        simpleControlDurationsNanos.addLast(nanos)
        while (simpleControlDurationsNanos.size > OSRS_PERFORMANCE_SAMPLE_LIMIT) simpleControlDurationsNanos.removeFirst()
    }

    private fun percentile95(samples: Collection<Long>): Long? {
        if (samples.isEmpty()) return null
        val sorted = samples.sorted()
        val index = (ceil(sorted.size * 0.95).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun formatMillis(nanos: Long?): String = nanos?.div(1_000_000.0)?.let { "%.1f".format(it) } ?: "–"

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val OSRS_LOG_TAG = "osrsUndergroundMaps"
        private const val OSRS_SAVED_REALM_ID = "osrs.saved.realm.id"
        private const val OSRS_SAVED_PLANE = "osrs.saved.realm.plane"
        private const val OSRS_PERFORMANCE_SAMPLE_LIMIT = 100
        private val OSRS_BASE_STYLE_JSON = """
            {
              "version": 8,
              "name": "OSRS Underground Maps Local Raster",
              "sources": {},
              "layers": [
                {
                  "id": "osrs-background",
                  "type": "background",
                  "paint": { "background-color": "#000000" }
                }
              ]
            }
        """.trimIndent()
    }
}

data class osrsLinkDialogOpenPhase(
    val ordinal: Int,
    val cold: Boolean,
    val realmId: String,
    val availableCount: Int,
    val unavailableCount: Int,
    val catalogCacheHit: Boolean,
    val catalogNanos: Long,
    val catalogBuildNanos: Long,
    val priorDialogCleanupNanos: Long,
    val dialogReused: Boolean,
    val viewConstructionNanos: Long,
    val initialFilterNanos: Long,
    val initialRowConversionNanos: Long,
    val initialAdapterSubmissionNanos: Long,
    val initialUpdateStrategy: String,
    val initialFilterObserverNanos: Long,
    val materialShowNanos: Long,
    val unclassifiedNanos: Long,
    val appNanos: Long,
    val showingAfterReturn: Boolean
) {
    val reconciledNanos: Long
        get() = catalogNanos +
            priorDialogCleanupNanos +
            viewConstructionNanos +
            initialFilterNanos +
            initialRowConversionNanos +
            initialAdapterSubmissionNanos +
            materialShowNanos +
            unclassifiedNanos
}

data class osrsMapDiagnostics(
    val candidate: String?,
    val activeRealmId: String?,
    val activeRealmName: String?,
    val activeRealmDisplayName: String?,
    val activeGroup: String?,
    val activeGroupLabel: String?,
    val activePlane: Int?,
    val activeSwitchRequestId: Long?,
    val sourceId: String?,
    val layerId: String?,
    val stagedAssetSha256: String?,
    val stagedAssetPath: String?,
    val realmBounds: List<Double>?,
    val cameraLatitude: Double?,
    val cameraLongitude: Double?,
    val cameraZoom: Double?,
    val cameraBearing: Double?,
    val styleGeneration: Int,
    val installedCameraRealmId: String?,
    val installedCameraPlane: Int?,
    val installedCameraRequestId: Long?,
    val installedCameraStyleGeneration: Int?,
    val cameraPersistenceMarker: String,
    val renderMarker: String,
    val switchRequestedAtNanos: Long?,
    val switchCompletedAtNanos: Long?,
    val lastAppOwnedSwitchNanos: Long?,
    val lastStageNanos: Long?,
    val switchP95Nanos: Long?,
    val simpleControlP95Nanos: Long?,
    val lastSelectorFilterNanos: Long?,
    val lastLinkDialogOpenNanos: Long?,
    val coldLinkDialogOpenNanos: Long?,
    val repeatedLinkDialogP95Nanos: Long?,
    val lastLinkFilterNanos: Long?,
    val lastLinkFilterResultCount: Int?,
    val fontScale: Float,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val selectorIdentityAccessibilityText: String?,
    val selectorIdentityTextLength: Int?,
    val selectorIdentityLineCount: Int?,
    val selectorIdentityMaxLines: Int,
    val selectorIdentityLastVisibleEnd: Int?,
    val selectorIdentityEllipsisCount: Int?,
    val selectorIdentityHonest: Boolean?,
    val selectorIdentityWidthPx: Int,
    val selectorIdentityHeightPx: Int,
    val topAndFloorControlsSeparated: Boolean?,
    val selectorAndStatusSeparated: Boolean?,
    val manifestRealmCount: Int?,
    val selectorRealmCount: Int?,
    val manifestAssetNonblank: Boolean?,
    val availableLinkCount: Int?,
    val unavailableLinkCount: Int?,
    val selectedLinkId: String?,
    val selectedLinkSideKey: String?,
    val selectedLinkTraversalDirection: String?,
    val linkTargetRealmId: String?,
    val linkTargetPlane: Int?,
    val linkTargetGameX: Int?,
    val linkTargetGameY: Int?,
    val linkMappedLatitude: Double?,
    val linkMappedLongitude: Double?,
    val linkMappedZoom: Double?,
    val linkMatchingLayoutCount: Int?,
    val linkAppliedMarker: String?,
    val error: String?
)

private data class osrsLinkNavigationState(
    val linkId: String,
    val linkSideKey: String,
    val traversalDirection: osrsRealmLinkTraversalDirection,
    val targetRealmId: String,
    val targetPlane: Int,
    val targetGameX: Int,
    val targetGameY: Int,
    val mappedLatitude: Double,
    val mappedLongitude: Double,
    val mappedZoom: Double,
    val matchingLayoutCount: Int,
    val appliedMarker: String
)
