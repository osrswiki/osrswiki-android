package com.omiyawaki.osrswiki.undergroundmaps.state

import com.omiyawaki.osrswiki.undergroundmaps.osrsTestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsRealmStateReducerTest {
    private val reducer = osrsRealmStateReducer()

    @Test
    fun `fresh state starts on true surface regardless of manifest order`() {
        val state = reducer.initial(osrsTestCatalog())

        assertEquals("cache-world-map:main", state.activeRealmId)
        assertEquals(0, state.activePlane)
    }

    @Test
    fun `instanced non-underground realm is selectable without code inventory`() {
        val initial = reducer.initial(osrsTestCatalog())

        val selected = reducer.reduce(
            initial,
            osrsRealmAction.SelectRealm("cache-world-map:lms-desert-island")
        )

        assertEquals("Last Man Standing Desert Island", selected.activeRealm.canonicalName)
        assertEquals(0, selected.activePlane)
        assertTrue(selected.switchRequestId > initial.switchRequestId)
    }

    @Test
    fun `camera and floor are remembered independently per stable realm ID`() {
        val surfaceCamera = osrsCameraState(-12.0, 5.0, 4.0, 15.0)
        var state = reducer.initial(osrsTestCatalog())
        state = reducer.reduce(state, osrsRealmAction.CameraChanged(surfaceCamera))
        state = reducer.reduce(state, osrsRealmAction.SelectRealm("cache-world-map:lms-desert-island"))
        state = reducer.reduce(state, osrsRealmAction.SelectPlane(1))
        state = reducer.reduce(state, osrsRealmAction.CameraChanged(osrsCameraState(0.0, 1.0, 3.0)))
        state = reducer.reduce(state, osrsRealmAction.SelectRealm("cache-world-map:main"))

        assertEquals(surfaceCamera, state.cameras.getValue("cache-world-map:main"))
        assertEquals(1, state.planesByRealm.getValue("cache-world-map:lms-desert-island"))
        assertEquals(0, state.activePlane)

        state = reducer.reduce(state, osrsRealmAction.SelectRealm("cache-world-map:lms-desert-island"))
        assertEquals(1, state.activePlane)
    }

    @Test
    fun `persisted selection restores only valid realm plane and camera values`() {
        val persisted = osrsPersistedRealmState(
            lastRealmId = "cache-world-map:lms-desert-island",
            cameras = mapOf(
                "cache-world-map:lms-desert-island" to osrsCameraState(0.0, 0.0, 5.0),
                "removed-realm" to osrsCameraState(0.0, 0.0, 5.0)
            ),
            planesByRealm = mapOf(
                "cache-world-map:lms-desert-island" to 1,
                "cache-world-map:main" to 99
            )
        )

        val restored = reducer.initial(osrsTestCatalog(), persisted)

        assertEquals("cache-world-map:lms-desert-island", restored.activeRealmId)
        assertEquals(1, restored.activePlane)
        assertEquals(setOf("cache-world-map:lms-desert-island"), restored.cameras.keys)
        assertEquals(null, restored.planesByRealm["cache-world-map:main"])
    }

    @Test
    fun `invalid floor selection is a no-op`() {
        val state = reducer.initial(osrsTestCatalog())
        val invalid = reducer.reduce(state, osrsRealmAction.SelectPlane(3))

        assertEquals(state, invalid)
    }

    @Test
    fun `style reload requests a new idempotent source install`() {
        val state = reducer.initial(osrsTestCatalog())
        val reloaded = reducer.reduce(state, osrsRealmAction.StyleReloaded)

        assertNotEquals(state.switchRequestId, reloaded.switchRequestId)
        assertEquals(state.activeRealmId, reloaded.activeRealmId)
        assertEquals(state.activePlane, reloaded.activePlane)
    }

    @Test
    fun `delayed A to B pause and recreation cannot persist the installed A camera into B`() {
        val catalog = osrsTestCatalog()
        val ownership = osrsCameraPersistenceOwnership()
        var state = reducer.initial(catalog)
        val surfaceCamera = osrsCameraState(-12.0, 5.0, 4.0)
        val surfaceIdentity = osrsInstalledCameraIdentity(
            realmId = state.activeRealmId,
            plane = state.activePlane,
            requestId = state.switchRequestId,
            styleGeneration = 1
        )
        assertTrue(ownership.markInstalled(surfaceIdentity, state, 1))
        state = reducer.reduce(
            state,
            osrsRealmAction.InstalledCameraChanged(
                surfaceIdentity.realmId,
                surfaceIdentity.plane,
                surfaceIdentity.requestId,
                surfaceCamera
            )
        )

        state = reducer.reduce(
            state,
            osrsRealmAction.SelectRealm("cache-world-map:lms-desert-island")
        )
        val requestedDestination = osrsCameraState(0.0, 0.0, 8.0)
        state = reducer.reduce(state, osrsRealmAction.CameraChanged(requestedDestination))
        assertEquals(null, ownership.authorization(state, 1))

        // onPause/onSaveInstanceState receives the still-visible A camera while B staging waits.
        val afterDelayedPause = reducer.reduce(
            state,
            osrsRealmAction.InstalledCameraChanged(
                surfaceIdentity.realmId,
                surfaceIdentity.plane,
                surfaceIdentity.requestId,
                osrsCameraState(-20.0, 8.0, 5.0)
            )
        )
        assertEquals(state, afterDelayedPause)
        assertEquals(requestedDestination, afterDelayedPause.cameras.getValue(state.activeRealmId))

        val recreated = reducer.initial(
            catalog = catalog,
            persisted = afterDelayedPause.persisted(),
            restoredRealmId = state.activeRealmId,
            restoredPlane = state.activePlane
        )
        assertEquals("cache-world-map:lms-desert-island", recreated.activeRealmId)
        assertEquals(requestedDestination, recreated.cameras.getValue(recreated.activeRealmId))
        assertFalse(
            ownership.markInstalled(
                surfaceIdentity,
                recreated,
                currentStyleGeneration = 2
            )
        )
    }

    @Test
    fun `rapid A to B to C and stale callbacks preserve only current installed identity`() {
        val ownership = osrsCameraPersistenceOwnership()
        var state = reducer.initial(osrsTestCatalog())
        val identityA = osrsInstalledCameraIdentity(
            state.activeRealmId,
            state.activePlane,
            state.switchRequestId,
            styleGeneration = 7
        )
        assertTrue(ownership.markInstalled(identityA, state, 7))

        state = reducer.reduce(
            state,
            osrsRealmAction.SelectRealm("cache-world-map:lms-desert-island")
        )
        val identityB = osrsInstalledCameraIdentity(
            state.activeRealmId,
            state.activePlane,
            state.switchRequestId,
            styleGeneration = 7
        )
        state = reducer.reduce(state, osrsRealmAction.SelectRealm("other-map-10042"))
        val identityC = osrsInstalledCameraIdentity(
            state.activeRealmId,
            state.activePlane,
            state.switchRequestId,
            styleGeneration = 7
        )

        assertFalse(ownership.markInstalled(identityB, state, 7))
        assertEquals(null, ownership.authorization(state, 7))
        assertTrue(ownership.markInstalled(identityC, state, 7))
        assertEquals(identityC, ownership.authorization(state, 7))

        val staleB = reducer.reduce(
            state,
            osrsRealmAction.InstalledCameraChanged(
                identityB.realmId,
                identityB.plane,
                identityB.requestId,
                osrsCameraState(0.0, 0.0, 4.0)
            )
        )
        assertEquals(state, staleB)
        val currentCamera = osrsCameraState(0.0, 0.0, 6.0)
        val persistedC = reducer.reduce(
            state,
            osrsRealmAction.InstalledCameraChanged(
                identityC.realmId,
                identityC.plane,
                identityC.requestId,
                currentCamera
            )
        )
        assertEquals(currentCamera, persistedC.cameras.getValue(identityC.realmId))
    }

    @Test
    fun `plane and style transitions invalidate old installed camera ownership`() {
        val ownership = osrsCameraPersistenceOwnership()
        var state = reducer.initial(osrsTestCatalog())
        state = reducer.reduce(
            state,
            osrsRealmAction.SelectRealm("cache-world-map:lms-desert-island")
        )
        val planeZero = osrsInstalledCameraIdentity(
            state.activeRealmId,
            state.activePlane,
            state.switchRequestId,
            styleGeneration = 3
        )
        assertTrue(ownership.markInstalled(planeZero, state, 3))

        state = reducer.reduce(state, osrsRealmAction.SelectPlane(1))
        assertEquals(null, ownership.authorization(state, 3))
        val planeOne = osrsInstalledCameraIdentity(
            state.activeRealmId,
            state.activePlane,
            state.switchRequestId,
            styleGeneration = 3
        )
        assertTrue(ownership.markInstalled(planeOne, state, 3))

        state = reducer.reduce(state, osrsRealmAction.StyleReloaded)
        assertEquals(null, ownership.authorization(state, 4))
        assertFalse(ownership.markInstalled(planeOne, state, 4))
        val reloaded = osrsInstalledCameraIdentity(
            state.activeRealmId,
            state.activePlane,
            state.switchRequestId,
            styleGeneration = 4
        )
        assertTrue(ownership.markInstalled(reloaded, state, 4))
    }
}
