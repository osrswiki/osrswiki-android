package com.omiyawaki.osrswiki.undergroundmaps.state

import com.omiyawaki.osrswiki.undergroundmaps.model.OSRS_REALM_GROUPS
import com.omiyawaki.osrswiki.undergroundmaps.model.cameraGeometryFingerprint
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCatalog
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCameraEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmRecord
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmSearch
import kotlinx.serialization.Serializable

@Serializable
data class osrsCameraState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double = 0.0,
    val tilt: Double = 0.0
) {
    fun isFinite(): Boolean = listOf(latitude, longitude, zoom, bearing, tilt).all(Double::isFinite)

    fun isWithin(asset: osrsRealmAsset): Boolean {
        return isFinite() &&
            latitude in asset.south..asset.north &&
            longitude in asset.west..asset.east &&
            osrsRealmCameraEnvelope.contains(asset, zoom)
    }
}

/** Exact identity of the raster/style whose camera is currently visible. */
data class osrsInstalledCameraIdentity(
    val realmId: String,
    val plane: Int,
    val requestId: Long,
    val styleGeneration: Int
) {
    fun owns(state: osrsRealmUiState, currentStyleGeneration: Int): Boolean =
        realmId == state.activeRealmId &&
            plane == state.activePlane &&
            requestId == state.switchRequestId &&
            styleGeneration == currentStyleGeneration
}

/** Fail-closed ownership gate for lifecycle and camera-idle persistence callbacks. */
class osrsCameraPersistenceOwnership {
    var installedIdentity: osrsInstalledCameraIdentity? = null
        private set

    fun markInstalled(
        identity: osrsInstalledCameraIdentity,
        state: osrsRealmUiState,
        currentStyleGeneration: Int
    ): Boolean {
        if (!identity.owns(state, currentStyleGeneration)) return false
        installedIdentity = identity
        return true
    }

    fun authorization(
        state: osrsRealmUiState,
        currentStyleGeneration: Int
    ): osrsInstalledCameraIdentity? = installedIdentity?.takeIf {
        it.owns(state, currentStyleGeneration)
    }

    fun clear() {
        installedIdentity = null
    }
}

@Serializable
data class osrsPersistedRealmState(
    val lastRealmId: String? = null,
    val cameras: Map<String, osrsCameraState> = emptyMap(),
    val planesByRealm: Map<String, Int> = emptyMap(),
    val cameraGeometryFingerprint: String? = null
)

data class osrsRealmUiState(
    val catalog: osrsRealmCatalog,
    val activeRealmId: String,
    val activePlane: Int,
    val cameras: Map<String, osrsCameraState>,
    val planesByRealm: Map<String, Int>,
    val query: String = "",
    val switchRequestId: Long = 1L
) {
    val activeRealm: osrsRealmRecord get() = catalog.byId.getValue(activeRealmId)
    val activeAsset: osrsRealmAsset get() = activeRealm.assetForPlane(activePlane)
        ?: error("No plane $activePlane asset for $activeRealmId")

    fun persisted(): osrsPersistedRealmState = osrsPersistedRealmState(
        lastRealmId = activeRealmId,
        cameras = cameras,
        planesByRealm = planesByRealm,
        cameraGeometryFingerprint = catalog.cameraGeometryFingerprint()
    )

    fun filteredSections(): Map<String, List<osrsRealmRecord>> {
        return OSRS_REALM_GROUPS.associateWith { group ->
            catalog.sections.getValue(group).filter { osrsRealmSearch.matches(it, query) }
        }
    }
}

sealed interface osrsRealmAction {
    data class SelectRealm(val realmId: String) : osrsRealmAction
    data class SelectPlane(val plane: Int) : osrsRealmAction
    data class CameraChanged(val camera: osrsCameraState) : osrsRealmAction
    data class InstalledCameraChanged(
        val realmId: String,
        val plane: Int,
        val requestId: Long,
        val camera: osrsCameraState
    ) : osrsRealmAction
    data class SearchChanged(val query: String) : osrsRealmAction
    data object StyleReloaded : osrsRealmAction
}

class osrsRealmStateReducer {
    fun initial(
        catalog: osrsRealmCatalog,
        persisted: osrsPersistedRealmState = osrsPersistedRealmState(),
        restoredRealmId: String? = null,
        restoredPlane: Int? = null
    ): osrsRealmUiState {
        val requestedId = restoredRealmId ?: persisted.lastRealmId
        val realm = requestedId?.let(catalog.byId::get) ?: catalog.surface
        val validCameras = if (
            persisted.cameraGeometryFingerprint == catalog.cameraGeometryFingerprint()
        ) {
            persisted.cameras.filter { (realmId, camera) ->
                catalog.byId.containsKey(realmId) && camera.isFinite()
            }
        } else {
            emptyMap()
        }
        val validPlanes = persisted.planesByRealm.mapNotNull { (realmId, plane) ->
            catalog.byId[realmId]?.takeIf { plane in it.planes }?.let { realmId to plane }
        }.toMap()
        val plane = restoredPlane
            ?.takeIf { it in realm.planes }
            ?: validPlanes[realm.id]
            ?: realm.defaultPlane

        return osrsRealmUiState(
            catalog = catalog,
            activeRealmId = realm.id,
            activePlane = plane,
            cameras = validCameras,
            planesByRealm = validPlanes + (realm.id to plane)
        )
    }

    fun reduce(state: osrsRealmUiState, action: osrsRealmAction): osrsRealmUiState {
        return when (action) {
            is osrsRealmAction.SelectRealm -> selectRealm(state, action.realmId)
            is osrsRealmAction.SelectPlane -> selectPlane(state, action.plane)
            is osrsRealmAction.CameraChanged -> state.copy(
                cameras = state.cameras + (state.activeRealmId to action.camera)
            )
            is osrsRealmAction.InstalledCameraChanged -> {
                if (
                    action.realmId == state.activeRealmId &&
                    action.plane == state.activePlane &&
                    action.requestId == state.switchRequestId
                ) {
                    state.copy(cameras = state.cameras + (action.realmId to action.camera))
                } else {
                    state
                }
            }
            is osrsRealmAction.SearchChanged -> state.copy(query = action.query)
            osrsRealmAction.StyleReloaded -> state.copy(switchRequestId = state.switchRequestId + 1L)
        }
    }

    private fun selectRealm(state: osrsRealmUiState, realmId: String): osrsRealmUiState {
        val realm = state.catalog.byId[realmId] ?: return state
        if (realmId == state.activeRealmId) return state
        val plane = state.planesByRealm[realmId]
            ?.takeIf { it in realm.planes }
            ?: realm.defaultPlane
        return state.copy(
            activeRealmId = realmId,
            activePlane = plane,
            planesByRealm = state.planesByRealm + (realmId to plane),
            query = "",
            switchRequestId = state.switchRequestId + 1L
        )
    }

    private fun selectPlane(state: osrsRealmUiState, plane: Int): osrsRealmUiState {
        if (plane !in state.activeRealm.planes || plane == state.activePlane) return state
        return state.copy(
            activePlane = plane,
            planesByRealm = state.planesByRealm + (state.activeRealmId to plane),
            switchRequestId = state.switchRequestId + 1L
        )
    }
}
