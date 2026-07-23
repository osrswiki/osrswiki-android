package com.omiyawaki.osrswiki.test

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypeCameraDescriptor
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypeHandoffState
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypePadding
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypeTerrainCapture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class MapPrototypeStateStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MapPrototypeStateStore.clearForTesting(context)
    }

    @After
    fun tearDown() {
        MapPrototypeStateStore.clearForTesting(context)
    }

    @Test
    fun imageAndCameraStateCommitAsOneGenerationIncludingBearingAndTilt() {
        val generation = MapPrototypeStateStore.reserveGeneration(context)
        val bitmap = bitmap(Color.rgb(64, 110, 72))
        val state = handoff(
            latitude = -18.125,
            longitude = 24.75,
            zoom = 6.375,
            bearing = 37.5,
            tilt = 12.0,
            floor = 2
        )

        val result = MapPrototypeStateStore.writeTerrainGenerationForTesting(
            context,
            osrsMapPrototypeTerrainCapture(generation, state, bitmap)
        )
        val loaded = MapPrototypeStateStore.loadSession(context)

        assertTrue(result.reason, result.success)
        assertEquals(generation, loaded?.generation)
        assertEquals("compatible", loaded?.previewStatus)
        assertEquals(37.5, loaded?.descriptor?.cameraBearing ?: 0.0, 0.0)
        assertEquals(12.0, loaded?.descriptor?.cameraTilt ?: 0.0, 0.0)
        assertEquals(2, loaded?.descriptor?.floor)
        assertTrue(loaded?.terrainPreview != null)
        loaded?.terrainPreview?.recycle()
        bitmap.recycle()
    }

    @Test
    fun rapidStopRejectsAnOlderInFlightPreviewInsteadOfPairingStalePixels() {
        val olderGeneration = MapPrototypeStateStore.reserveGeneration(context)
        val oldBitmap = bitmap(Color.RED)
        val imagePublished = CountDownLatch(1)
        val allowWriterToContinue = CountDownLatch(1)
        val writerResult = AtomicReference<MapPrototypeStateStore.WriteResult>()
        MapPrototypeStateStore.faultInjectorForTesting = { point ->
            if (point == MapPrototypeStateStore.FaultPoint.AFTER_IMAGE_PUBLISH) {
                imagePublished.countDown()
                check(allowWriterToContinue.await(5, TimeUnit.SECONDS))
            }
        }
        val writer = Thread {
            writerResult.set(
                MapPrototypeStateStore.writeTerrainGenerationForTesting(
                    context,
                    osrsMapPrototypeTerrainCapture(
                        olderGeneration,
                        handoff(latitude = -10.0, bearing = 15.0),
                        oldBitmap
                    )
                )
            )
        }
        writer.start()
        assertTrue(imagePublished.await(5, TimeUnit.SECONDS))

        val newerGeneration = MapPrototypeStateStore.reserveGeneration(context)
        val newerState = handoff(latitude = -11.0, bearing = 82.0, floor = 3)
        assertTrue(MapPrototypeStateStore.publishStateOnly(context, newerGeneration, newerState).success)
        allowWriterToContinue.countDown()
        writer.join(5_000)
        MapPrototypeStateStore.faultInjectorForTesting = null

        val loaded = MapPrototypeStateStore.loadSession(context)
        assertFalse(writerResult.get().success)
        assertEquals(newerGeneration, loaded?.generation)
        assertEquals("state-only-generation", loaded?.previewStatus)
        assertNull(loaded?.terrainPreview)
        assertEquals(-11.0, loaded?.descriptor?.cameraLatitude ?: 0.0, 0.0)
        assertEquals(82.0, loaded?.descriptor?.cameraBearing ?: 0.0, 0.0)
        assertEquals(3, loaded?.descriptor?.floor)
        oldBitmap.recycle()
    }

    @Test
    fun everyPublicationFaultLeavesTheLastCompletedGenerationActive() {
        val baselineGeneration = MapPrototypeStateStore.reserveGeneration(context)
        val baselineBitmap = bitmap(Color.GREEN)
        val baselineResult = MapPrototypeStateStore.writeTerrainGenerationForTesting(
                context,
                osrsMapPrototypeTerrainCapture(
                    baselineGeneration,
                    handoff(latitude = -20.0),
                    baselineBitmap
                )
            )
        assertTrue(baselineResult.reason, baselineResult.success)

        MapPrototypeStateStore.FaultPoint.entries.forEachIndexed { index, point ->
            val generation = MapPrototypeStateStore.reserveGeneration(context)
            val failedBitmap = bitmap(Color.rgb(index * 30, 20, 40))
            MapPrototypeStateStore.faultInjectorForTesting = { current ->
                if (current == point) error("injected-$point")
            }
            val result = MapPrototypeStateStore.writeTerrainGenerationForTesting(
                context,
                osrsMapPrototypeTerrainCapture(
                    generation,
                    handoff(latitude = -30.0 - index),
                    failedBitmap
                )
            )
            MapPrototypeStateStore.faultInjectorForTesting = null
            assertFalse("$point must fail", result.success)
            val loaded = MapPrototypeStateStore.loadSession(context)
            assertEquals("$point must preserve active generation", baselineGeneration, loaded?.generation)
            loaded?.terrainPreview?.recycle()
            failedBitmap.recycle()
        }
        baselineBitmap.recycle()
    }

    @Test
    fun successfulPublicationsPruneObsoleteGenerationDescriptorsPreviewsAndTemps() {
        val firstGeneration = MapPrototypeStateStore.reserveGeneration(context)
        val firstBitmap = bitmap(Color.rgb(10, 80, 120))
        val firstResult = MapPrototypeStateStore.writeTerrainGenerationForTesting(
            context,
            osrsMapPrototypeTerrainCapture(
                firstGeneration,
                handoff(latitude = -15.0),
                firstBitmap
            )
        )
        assertTrue(firstResult.reason, firstResult.success)
        File(handoffDirectory(), "generation-$firstGeneration.json.tmp").writeText("stale descriptor temp")
        File(handoffDirectory(), "generation-$firstGeneration.webp.tmp").writeText("stale preview temp")

        val secondGeneration = MapPrototypeStateStore.reserveGeneration(context)
        val secondResult = MapPrototypeStateStore.publishStateOnly(
            context,
            secondGeneration,
            handoff(latitude = -16.0)
        )
        assertTrue(secondResult.reason, secondResult.success)
        assertEquals(
            setOf(
                "active-generation.json",
                "generation-$secondGeneration.json"
            ),
            handoffFileNames()
        )
        assertEquals(secondGeneration, MapPrototypeStateStore.loadSession(context)?.generation)

        val thirdGeneration = MapPrototypeStateStore.reserveGeneration(context)
        val thirdBitmap = bitmap(Color.rgb(80, 120, 10))
        val thirdResult = MapPrototypeStateStore.writeTerrainGenerationForTesting(
            context,
            osrsMapPrototypeTerrainCapture(
                thirdGeneration,
                handoff(latitude = -17.0),
                thirdBitmap
            )
        )
        assertTrue(thirdResult.reason, thirdResult.success)
        val loaded = MapPrototypeStateStore.loadSession(context)
        assertEquals(thirdGeneration, loaded?.generation)
        assertEquals("compatible", loaded?.previewStatus)
        assertTrue(loaded?.terrainPreview != null)
        assertEquals(
            setOf(
                "active-generation.json",
                "generation-$thirdGeneration.json",
                "generation-$thirdGeneration.webp"
            ),
            handoffFileNames()
        )

        loaded?.terrainPreview?.recycle()
        firstBitmap.recycle()
        thirdBitmap.recycle()
    }

    @Test
    fun rejectedLifecyclePreviewCopyDeletesOnlyItsCopiedGenerationAfterNewerPreviewWins() {
        val activeGeneration = MapPrototypeStateStore.reserveGeneration(context)
        val activeBitmap = bitmap(Color.rgb(25, 90, 140))
        val activeState = handoff(
            latitude = -31.25,
            longitude = 42.5,
            zoom = 7.25,
            bearing = 22.5,
            tilt = 9.0,
            floor = 1
        )
        val activeResult = MapPrototypeStateStore.writeTerrainGenerationForTesting(
            context,
            osrsMapPrototypeTerrainCapture(activeGeneration, activeState, activeBitmap)
        )
        assertTrue(activeResult.reason, activeResult.success)
        val activeLoaded = MapPrototypeStateStore.loadSession(context)
        assertEquals(activeGeneration, activeLoaded?.generation)
        assertEquals("compatible", activeLoaded?.previewStatus)
        activeLoaded?.terrainPreview?.recycle()

        val lifecycleGeneration = AtomicReference<Long>()
        val winningGeneration = AtomicReference<Long>()
        MapPrototypeStateStore.lifecycleCopySourceOpenedForTesting = { copiedGeneration ->
            lifecycleGeneration.set(copiedGeneration)
            val nextGeneration = MapPrototypeStateStore.reserveGeneration(context)
            winningGeneration.set(nextGeneration)
            val winningBitmap = bitmap(Color.rgb(210, 120, 45))
            val winningResult = MapPrototypeStateStore.writeTerrainGenerationForTesting(
                context,
                osrsMapPrototypeTerrainCapture(
                    nextGeneration,
                    handoff(
                        latitude = -32.75,
                        longitude = 43.125,
                        zoom = 8.0,
                        bearing = 91.5,
                        tilt = 17.0,
                        floor = 4
                    ),
                    winningBitmap
                )
            )
            winningBitmap.recycle()
            assertTrue(winningResult.reason, winningResult.success)
        }
        val lifecycleResult = try {
            MapPrototypeStateStore.publishLifecycleState(context, activeState)
        } finally {
            MapPrototypeStateStore.lifecycleCopySourceOpenedForTesting = null
        }

        val rejectedGeneration = lifecycleGeneration.get()
        val preservedGeneration = winningGeneration.get()
        assertFalse(lifecycleResult.reason, lifecycleResult.success)
        assertEquals(rejectedGeneration, lifecycleResult.generation)
        assertTrue(lifecycleResult.reason.startsWith("obsolete-generation-active="))
        assertFalse(File(handoffDirectory(), "generation-$rejectedGeneration.json").exists())
        assertFalse(File(handoffDirectory(), "generation-$rejectedGeneration.webp").exists())
        assertFalse(File(handoffDirectory(), "generation-$rejectedGeneration.webp.tmp").exists())
        assertTrue(File(handoffDirectory(), "generation-$preservedGeneration.json").isFile)
        assertTrue(File(handoffDirectory(), "generation-$preservedGeneration.webp").isFile)

        val loaded = MapPrototypeStateStore.loadSession(context)
        assertEquals(preservedGeneration, loaded?.generation)
        assertEquals("compatible", loaded?.previewStatus)
        assertTrue(loaded?.terrainPreview != null)
        assertEquals(91.5, loaded?.descriptor?.cameraBearing ?: 0.0, 0.0)
        assertEquals(17.0, loaded?.descriptor?.cameraTilt ?: 0.0, 0.0)
        assertEquals(4, loaded?.descriptor?.floor)
        assertEquals(
            setOf(
                "active-generation.json",
                "generation-$preservedGeneration.json",
                "generation-$preservedGeneration.webp"
            ),
            handoffFileNames()
        )

        loaded?.terrainPreview?.recycle()
        activeBitmap.recycle()
    }

    @Test
    fun failedPointerCommitPreservesLastActiveGenerationUntilNextSuccessfulPublish() {
        val baselineGeneration = MapPrototypeStateStore.reserveGeneration(context)
        val baselineBitmap = bitmap(Color.rgb(40, 130, 60))
        val baselineResult = MapPrototypeStateStore.writeTerrainGenerationForTesting(
            context,
            osrsMapPrototypeTerrainCapture(
                baselineGeneration,
                handoff(latitude = -21.0),
                baselineBitmap
            )
        )
        assertTrue(baselineResult.reason, baselineResult.success)

        val failedGeneration = MapPrototypeStateStore.reserveGeneration(context)
        val failedBitmap = bitmap(Color.rgb(180, 20, 30))
        MapPrototypeStateStore.faultInjectorForTesting = { point ->
            if (point == MapPrototypeStateStore.FaultPoint.BEFORE_ACTIVE_POINTER_COMMIT) {
                error("injected-pointer-fault")
            }
        }
        val failedResult = MapPrototypeStateStore.writeTerrainGenerationForTesting(
            context,
            osrsMapPrototypeTerrainCapture(
                failedGeneration,
                handoff(latitude = -22.0),
                failedBitmap
            )
        )
        MapPrototypeStateStore.faultInjectorForTesting = null

        assertFalse(failedResult.success)
        assertTrue(File(handoffDirectory(), "generation-$baselineGeneration.json").isFile)
        assertTrue(File(handoffDirectory(), "generation-$baselineGeneration.webp").isFile)
        val preserved = MapPrototypeStateStore.loadSession(context)
        assertEquals(baselineGeneration, preserved?.generation)
        assertEquals("compatible", preserved?.previewStatus)
        assertTrue(preserved?.terrainPreview != null)
        assertFalse(File(handoffDirectory(), "generation-$failedGeneration.webp").exists())

        val finalGeneration = MapPrototypeStateStore.reserveGeneration(context)
        val finalResult = MapPrototypeStateStore.publishStateOnly(
            context,
            finalGeneration,
            handoff(latitude = -23.0)
        )
        assertTrue(finalResult.reason, finalResult.success)
        assertEquals(finalGeneration, MapPrototypeStateStore.loadSession(context)?.generation)
        assertEquals(
            setOf(
                "active-generation.json",
                "generation-$finalGeneration.json"
            ),
            handoffFileNames()
        )

        preserved?.terrainPreview?.recycle()
        baselineBitmap.recycle()
        failedBitmap.recycle()
    }

    @Test
    fun corruptOrMissingImageFallsBackWithoutDiscardingMatchingState() {
        val generation = MapPrototypeStateStore.reserveGeneration(context)
        val source = bitmap(Color.BLUE)
        val initialResult = MapPrototypeStateStore.writeTerrainGenerationForTesting(
                context,
                osrsMapPrototypeTerrainCapture(generation, handoff(bearing = 45.0), source)
            )
        assertTrue(initialResult.reason, initialResult.success)
        val first = MapPrototypeStateStore.loadSession(context)!!
        first.terrainPreview?.recycle()
        val image = File(
            context.filesDir,
            "map-prototype-handoff/${first.descriptor.imageFileName}"
        )
        image.writeBytes(byteArrayOf(1, 2, 3, 4))

        val corrupt = MapPrototypeStateStore.loadSession(context)
        assertEquals(generation, corrupt?.generation)
        assertNull(corrupt?.terrainPreview)
        assertEquals("preview-missing-corrupt-or-hash-mismatched", corrupt?.previewStatus)
        assertEquals(45.0, corrupt?.descriptor?.cameraBearing ?: 0.0, 0.0)

        image.delete()
        val missing = MapPrototypeStateStore.loadSession(context)
        assertEquals(generation, missing?.generation)
        assertNull(missing?.terrainPreview)
        source.recycle()
    }

    @Test
    fun heightDensityFontOrientationViewportAndPaddingChangesRejectPreview() {
        val generation = MapPrototypeStateStore.reserveGeneration(context)
        val source = bitmap(Color.MAGENTA)
        val initialResult = MapPrototypeStateStore.writeTerrainGenerationForTesting(
                context,
                osrsMapPrototypeTerrainCapture(generation, handoff(), source)
            )
        assertTrue(initialResult.reason, initialResult.success)
        val session = MapPrototypeStateStore.loadSession(context)!!
        val descriptor = session.descriptor
        session.terrainPreview?.recycle()

        assertEquals(
            "display-height-mismatch",
            MapPrototypeStateStore.previewCompatibility(
                context,
                descriptor.copy(displayHeightPx = descriptor.displayHeightPx + 1)
            )
        )
        assertEquals(
            "density-mismatch",
            MapPrototypeStateStore.previewCompatibility(
                context,
                descriptor.copy(densityDpi = descriptor.densityDpi + 1)
            )
        )
        assertEquals(
            "font-scale-mismatch",
            MapPrototypeStateStore.previewCompatibility(
                context,
                descriptor.copy(fontScale = descriptor.fontScale + 0.5f)
            )
        )
        val otherOrientation = if (descriptor.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
        assertEquals(
            "orientation-mismatch",
            MapPrototypeStateStore.previewCompatibility(
                context,
                descriptor.copy(orientation = otherOrientation)
            )
        )
        assertEquals(
            "viewport-width-mismatch",
            MapPrototypeStateStore.previewCompatibility(
                context,
                descriptor,
                MapPrototypeStateStore.PreviewExpectations(
                    viewportWidthPx = descriptor.viewportWidthPx + 1
                )
            )
        )
        assertEquals(
            "padding-mismatch",
            MapPrototypeStateStore.previewCompatibility(
                context,
                descriptor,
                MapPrototypeStateStore.PreviewExpectations(
                    padding = descriptor.padding.copy(topPx = descriptor.padding.topPx + 1)
                )
            )
        )
        source.recycle()
    }

    private fun handoff(
        latitude: Double = -18.0,
        longitude: Double = 23.0,
        zoom: Double = 6.0,
        bearing: Double = 0.0,
        tilt: Double = 0.0,
        floor: Int = 0
    ): osrsMapPrototypeHandoffState {
        val semantic = Bundle().apply {
            putDouble("prototype_camera_lat", latitude)
            putDouble("prototype_camera_lon", longitude)
            putDouble("prototype_camera_zoom", zoom)
            putDouble("prototype_camera_bearing", bearing)
            putDouble("prototype_camera_tilt", tilt)
        }
        val state = Bundle().apply {
            putInt("state_current_floor", floor)
            putBundle("state_semantic_prototype", semantic)
        }
        return osrsMapPrototypeHandoffState(
            fragmentState = state,
            camera = osrsMapPrototypeCameraDescriptor(
                latitude,
                longitude,
                zoom,
                bearing,
                tilt
            ),
            floor = floor,
            viewportWidthPx = 1080,
            viewportHeightPx = 1600,
            padding = osrsMapPrototypePadding(0, 180, 0, 220)
        )
    }

    private fun bitmap(color: Int): Bitmap {
        return Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
    }

    private fun handoffDirectory(): File {
        return File(context.filesDir, "map-prototype-handoff")
    }

    private fun handoffFileNames(): Set<String> {
        return handoffDirectory().list()?.toSet().orEmpty()
    }
}
