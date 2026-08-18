package com.omiyawaki.osrswiki.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapLifecycleContractSourceTest {

    @Test
    fun preloaderPublishesFailureStateAndHasExplicitDestroyPath() {
        val source = sourceFile("AndroidMapPreloader.kt").readText()

        assertTrue(source.contains("sealed class PreloadState"))
        assertTrue(source.contains("val preloadState: LiveData<PreloadState>"))
        assertTrue(source.contains("PreloadState.Failed"))
        assertTrue(source.contains("fun destroy()"))
        assertTrue(source.contains("sharedMapView?.onDestroy()"))
        assertTrue(source.contains("rootView?.removeView(sharedMapContainer)"))
    }

    @Test
    fun readyPreloaderIsIdempotentAndDoesNotRecreateMapView() {
        val source = sourceFile("AndroidMapPreloader.kt").readText()
        val method = source.substringAfter("suspend fun preloadMapInBackground")
            .substringBefore("/**\n     * Create the shared MapView")

        assertTrue(method.contains("if (isMapReady)"))
        assertTrue(method.contains("PreloadState.Ready"))
        assertTrue(method.indexOf("if (isMapReady)") < method.indexOf("performPreload(context, generation)"))
    }

    @Test
    fun mbtilesCopyFailureStopsLayerCreation() {
        val source = sourceFile("AndroidMapPreloader.kt").readText()
        val preCreateMethod = source.substringAfter("private suspend fun preCreateAllFloorLayers")
            .substringBefore("/**\n     * Copy map assets")
        val copyMethod = source.substringAfter("private fun copyMapAssets")
            .substringBefore("/**\n     * Move the shared map")

        assertTrue(preCreateMethod.contains("copyMapAssets(context).getOrThrow()"))
        assertTrue(copyMethod.contains("Result<Unit>"))
        assertTrue(copyMethod.contains("return Result.failure"))
        assertFalse(preCreateMethod.contains("// Copy map assets to internal storage\n        copyMapAssets(context)\n"))
    }

    @Test
    fun mapFragmentsDoNotDriveMapViewLifecycleFromHiddenCallbacks() {
        listOf("StandardNavigationMapFragment.kt", "MapFragment.kt").forEach { fileName ->
            val source = sourceFile(fileName).readText()
            val hiddenCallback = source.substringAfter("override fun onHiddenChanged")
                .substringBeforeLast("}")

            assertFalse("$fileName must not call MapView.onPause from onHiddenChanged", hiddenCallback.contains(".onPause()"))
            assertFalse("$fileName must not call MapView.onResume from onHiddenChanged", hiddenCallback.contains(".onResume()"))
            assertFalse("$fileName must not call MapView.onStart from onHiddenChanged", hiddenCallback.contains(".onStart()"))
            assertFalse("$fileName must not call MapView.onStop from onHiddenChanged", hiddenCallback.contains(".onStop()"))
        }
    }

    @Test
    fun mainActivityDoesNotEagerlyCreateSharedMapForArticleOnlyLaunches() {
        val mainActivity = File("src/main/java/com/omiyawaki/osrswiki/MainActivity.kt").readText()
        val standardMapFragment = sourceFile("StandardNavigationMapFragment.kt").readText()

        assertFalse(
            "MainActivity startup must not eagerly create the shared MapLibre MapView for article-only navigation",
            mainActivity.contains("preloadMapInBackground(this@MainActivity)")
        )
        assertTrue(
            "The map tab should still lazily start the shared map preloader when it is shown",
            standardMapFragment.contains("preloader.requestPreload(requireContext())")
        )
    }

    @Test
    fun preloadWorkIsProcessOwnedAndCallerCancellationIsNotFailure() {
        val source = sourceFile("AndroidMapPreloader.kt").readText()

        assertTrue(source.contains("CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)"))
        assertTrue(source.contains("class ProcessOwnedPreloadCoordinator"))
        assertTrue(source.contains("private val mutex = Mutex()"))
        assertTrue(source.contains("inFlight?.takeIf { it.isActive }"))
        assertTrue(source.contains("processScope.async"))
        assertTrue(source.contains("deferred.await()"))
        assertTrue(source.contains("catch (cancelled: CancellationException)"))
        assertFalse(
            "Cancellation must not be translated to a functional preload failure",
            source.substringAfter("catch (cancelled: CancellationException)")
                .substringBefore("catch (failure: Exception)")
                .contains("PreloadState.Failed")
        )
    }

    @Test
    fun sharedMapCallbacksAreOwnerTokenGated() {
        val source = sourceFile("StandardNavigationMapFragment.kt").readText()
        val preloader = sourceFile("AndroidMapPreloader.kt").readText()

        assertTrue(preloader.contains("class MapAttachment"))
        assertTrue(preloader.contains("val ownerToken: Long"))
        assertTrue(preloader.contains("fun isActiveOwner(ownerToken: Long"))
        assertTrue(source.contains("if (!isCurrentMapOwner(sharedMapView)) return@getMapAsync"))
        assertTrue(source.contains("isCurrentMapOwner(mapView, ownerToken, requireResumed = true)"))
        assertTrue(source.contains("detachFromMainMapContainer(token)"))
    }

    @Test
    fun productionRealmMapIsLazilyAddedOnlyWhenMapTabIsSelected() {
        val mainActivity = File("src/main/java/com/omiyawaki/osrswiki/MainActivity.kt").readText()
        val initialTransaction = mainActivity.substringAfter("supportFragmentManager.beginTransaction()")
            .substringBefore(".runOnCommit")

        assertTrue(
            "MainActivity should construct the production realm map fragment",
            mainActivity.contains("mapFragment = osrsUndergroundMapsFragment()")
        )
        assertFalse(
            "Home startup must not eagerly add the production realm map fragment",
            initialTransaction.contains(".add(R.id.nav_host_container, mapFragment")
        )
        assertTrue(
            "The selected map fragment should be lazily added to the persistent navigation host",
            mainActivity.contains("transaction.add(R.id.nav_host_container, fragment, tag)")
        )
        assertFalse(
            "Selecting Map must not leave the persistent navigation host for a standalone activity",
            mainActivity.contains("startActivity(Intent(this, osrsUndergroundMapsActivity::class.java))")
        )
    }

    private fun sourceFile(name: String): File {
        return File("src/main/java/com/omiyawaki/osrswiki/ui/map", name)
    }
}
