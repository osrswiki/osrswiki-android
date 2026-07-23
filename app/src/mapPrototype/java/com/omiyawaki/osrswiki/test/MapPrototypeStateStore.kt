package com.omiyawaki.osrswiki.test

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.system.Os
import android.util.Base64
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypeHandoffState
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypePadding
import com.omiyawaki.osrswiki.ui.map.osrsMapPrototypeTerrainCapture
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.abs

object MapPrototypeStateStore {
    data class WriteResult(
        val success: Boolean,
        val generation: Long,
        val reason: String
    )

    data class PersistedSession(
        val fragmentState: Bundle,
        val terrainPreview: Bitmap?,
        val generation: Long,
        val previewStatus: String,
        val descriptor: HandoffDescriptor
    )

    data class PreviewExpectations(
        val viewportWidthPx: Int? = null,
        val viewportHeightPx: Int? = null,
        val padding: osrsMapPrototypePadding? = null
    )

    data class HandoffDescriptor(
        val version: Int,
        val generation: Long,
        val fragmentStateBase64: String,
        val cameraLatitude: Double,
        val cameraLongitude: Double,
        val cameraZoom: Double,
        val cameraBearing: Double,
        val cameraTilt: Double,
        val floor: Int,
        val viewportWidthPx: Int,
        val viewportHeightPx: Int,
        val displayWidthPx: Int,
        val displayHeightPx: Int,
        val density: Float,
        val densityDpi: Int,
        val fontScale: Float,
        val orientation: Int,
        val padding: osrsMapPrototypePadding,
        val imageFileName: String?,
        val imageWidthPx: Int?,
        val imageHeightPx: Int?,
        val imageSha256: String?
    ) {
        val hasPreview: Boolean
            get() = imageFileName != null && imageWidthPx != null &&
                imageHeightPx != null && imageSha256 != null
    }

    internal enum class FaultPoint {
        BEFORE_IMAGE_WRITE,
        AFTER_IMAGE_PUBLISH,
        BEFORE_DESCRIPTOR_WRITE,
        AFTER_DESCRIPTOR_PUBLISH,
        BEFORE_ACTIVE_POINTER_COMMIT
    }

    const val EXTRA_RESTORE_PERSISTED_STATE = "map_prototype_restore_persisted_state"

    private const val DESCRIPTOR_VERSION = 1
    private const val PREFERENCES = "map_prototype_navigation_state_v2"
    private const val KEY_NEXT_GENERATION = "next_generation"
    private const val STORE_DIRECTORY = "map-prototype-handoff"
    private const val ACTIVE_POINTER = "active-generation.json"
    private const val PERSISTED_PREVIEW_MAX_WIDTH_PX = 720
    private val managedGenerationFilePattern = Regex("""generation-(\d+)\.(json|webp)(\.tmp)?""")
    private val generationLock = Any()
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "map-prototype-handoff-writer")
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    internal var faultInjectorForTesting: ((FaultPoint) -> Unit)? = null

    @Volatile
    internal var lifecycleCopySourceOpenedForTesting: ((Long) -> Unit)? = null

    fun reserveGeneration(context: Context): Long = synchronized(generationLock) {
        val applicationContext = context.applicationContext
        val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val activeGeneration = readActiveGeneration(applicationContext) ?: 0L
        val next = maxOf(preferences.getLong(KEY_NEXT_GENERATION, 0L), activeGeneration) + 1L
        check(preferences.edit().putLong(KEY_NEXT_GENERATION, next).commit()) {
            "Could not reserve map handoff generation"
        }
        next
    }

    fun publishStateOnly(
        context: Context,
        generation: Long,
        handoffState: osrsMapPrototypeHandoffState
    ): WriteResult {
        return try {
            val descriptor = descriptorFor(
                context = context.applicationContext,
                generation = generation,
                state = handoffState,
                image = null
            )
            publishDescriptorAndPointer(context.applicationContext, descriptor)
        } catch (failure: Exception) {
            WriteResult(false, generation, failure.message ?: failure.javaClass.simpleName)
        }
    }

    fun publishLifecycleState(
        context: Context,
        handoffState: osrsMapPrototypeHandoffState
    ): WriteResult {
        val applicationContext = context.applicationContext
        val generation = reserveGeneration(applicationContext)
        val priorSession = loadSession(applicationContext)
        val prior = priorSession?.descriptor
        priorSession?.terrainPreview?.recycle()
        if (
            prior != null &&
            prior.hasPreview &&
            terrainGeometryMatches(applicationContext, prior, handoffState)
        ) {
            val source = File(storeDirectory(applicationContext), prior.imageFileName.orEmpty())
            if (source.isFile && sha256(source) == prior.imageSha256) {
                val target = File(storeDirectory(applicationContext), imageFileName(generation))
                val temporary = File(target.parentFile, "${target.name}.tmp")
                try {
                    source.inputStream().buffered().use { input ->
                        lifecycleCopySourceOpenedForTesting?.invoke(generation)
                        FileOutputStream(temporary).use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                    atomicReplace(temporary, target)
                    val descriptor = descriptorFor(
                        context = applicationContext,
                        generation = generation,
                        state = handoffState,
                        image = ImageMetadata(
                            target.name,
                            prior.imageWidthPx!!,
                            prior.imageHeightPx!!,
                            sha256(target)
                        )
                    )
                    return publishDescriptorAndPointer(applicationContext, descriptor).also { result ->
                        if (!result.success) {
                            target.delete()
                            File(target.parentFile, descriptorFileName(generation)).delete()
                        }
                    }
                } catch (failure: Exception) {
                    temporary.delete()
                    target.delete()
                    File(target.parentFile, descriptorFileName(generation)).delete()
                }
            }
        }
        return publishStateOnly(applicationContext, generation, handoffState)
    }

    fun enqueueTerrainPreview(
        context: Context,
        capture: osrsMapPrototypeTerrainCapture,
        completion: (WriteResult) -> Unit
    ) {
        val applicationContext = context.applicationContext
        writer.execute {
            val result = writeTerrainGeneration(applicationContext, capture)
            if (!capture.bitmap.isRecycled) capture.bitmap.recycle()
            mainHandler.post { completion(result) }
        }
    }

    fun loadSession(
        context: Context,
        expectations: PreviewExpectations = PreviewExpectations()
    ): PersistedSession? {
        val applicationContext = context.applicationContext
        val pointer = readJson(File(storeDirectory(applicationContext), ACTIVE_POINTER)) ?: return null
        if (pointer.optInt("version", -1) != DESCRIPTOR_VERSION) return null
        val generation = pointer.optLong("generation", -1L).takeIf { it > 0L } ?: return null
        val descriptorFile = pointer.optString("descriptor", "").takeIf { it.isNotBlank() }
            ?: return null
        val descriptor = readJson(File(storeDirectory(applicationContext), descriptorFile))
            ?.let(::descriptorFromJson)
            ?.takeIf { it.generation == generation && it.version == DESCRIPTOR_VERSION }
            ?: return null
        val state = decodeBundle(descriptor.fragmentStateBase64) ?: return null
        if (!fragmentStateMatchesDescriptor(state, descriptor)) return null
        val compatibility = previewCompatibility(applicationContext, descriptor, expectations)
        val preview = if (compatibility == "compatible" && descriptor.hasPreview) {
            loadAndValidatePreview(applicationContext, descriptor)
        } else {
            null
        }
        val status = when {
            !descriptor.hasPreview -> "state-only-generation"
            compatibility != "compatible" -> compatibility
            preview == null -> "preview-missing-corrupt-or-hash-mismatched"
            else -> "compatible"
        }
        return PersistedSession(state, preview, generation, status, descriptor)
    }

    internal fun previewCompatibility(
        context: Context,
        descriptor: HandoffDescriptor,
        expectations: PreviewExpectations = PreviewExpectations()
    ): String {
        val resources = context.resources
        val metrics = resources.displayMetrics
        val configuration = resources.configuration
        if (descriptor.orientation != configuration.orientation) return "orientation-mismatch"
        if (descriptor.displayWidthPx != metrics.widthPixels) return "display-width-mismatch"
        if (descriptor.displayHeightPx != metrics.heightPixels) return "display-height-mismatch"
        if (descriptor.densityDpi != metrics.densityDpi || abs(descriptor.density - metrics.density) > 0.001f) {
            return "density-mismatch"
        }
        if (abs(descriptor.fontScale - configuration.fontScale) > 0.001f) return "font-scale-mismatch"
        if (descriptor.viewportWidthPx <= 0 || descriptor.viewportHeightPx <= 0) {
            return "invalid-viewport"
        }
        if (expectations.viewportWidthPx != null &&
            expectations.viewportWidthPx != descriptor.viewportWidthPx
        ) return "viewport-width-mismatch"
        if (expectations.viewportHeightPx != null &&
            expectations.viewportHeightPx != descriptor.viewportHeightPx
        ) return "viewport-height-mismatch"
        if (expectations.padding != null && expectations.padding != descriptor.padding) {
            return "padding-mismatch"
        }
        val padding = descriptor.padding
        if (padding.leftPx < 0 || padding.topPx < 0 || padding.rightPx < 0 || padding.bottomPx < 0) {
            return "invalid-padding"
        }
        if (padding.leftPx + padding.rightPx >= descriptor.viewportWidthPx ||
            padding.topPx + padding.bottomPx >= descriptor.viewportHeightPx
        ) return "padding-outside-viewport"
        return "compatible"
    }

    fun clearForTesting(context: Context) {
        writer.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
        synchronized(generationLock) {
            storeDirectory(context.applicationContext).deleteRecursively()
            context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            faultInjectorForTesting = null
            lifecycleCopySourceOpenedForTesting = null
        }
    }

    internal fun writeTerrainGenerationForTesting(
        context: Context,
        capture: osrsMapPrototypeTerrainCapture
    ): WriteResult = writeTerrainGeneration(context.applicationContext, capture)

    private fun writeTerrainGeneration(
        context: Context,
        capture: osrsMapPrototypeTerrainCapture
    ): WriteResult {
        val generation = capture.generation
        var persistedBitmap: Bitmap? = null
        val directory = storeDirectory(context)
        val imageFile = File(directory, imageFileName(generation))
        val temporaryImage = File(directory, "${imageFile.name}.tmp")
        return try {
            fault(FaultPoint.BEFORE_IMAGE_WRITE)
            persistedBitmap = scaledPreview(capture.bitmap)
            FileOutputStream(temporaryImage).use { output ->
                @Suppress("DEPRECATION")
                check(persistedBitmap.compress(Bitmap.CompressFormat.WEBP, 94, output))
                output.fd.sync()
            }
            atomicReplace(temporaryImage, imageFile)
            fault(FaultPoint.AFTER_IMAGE_PUBLISH)
            val descriptor = descriptorFor(
                context = context,
                generation = generation,
                state = capture.handoffState,
                image = ImageMetadata(
                    fileName = imageFile.name,
                    widthPx = persistedBitmap.width,
                    heightPx = persistedBitmap.height,
                    sha256 = sha256(imageFile)
                )
            )
            publishDescriptorAndPointer(context, descriptor).also { result ->
                if (!result.success) {
                    imageFile.delete()
                    File(storeDirectory(context), descriptorFileName(generation)).delete()
                }
            }
        } catch (failure: Exception) {
            temporaryImage.delete()
            if (readActiveGeneration(context) != generation) imageFile.delete()
            WriteResult(false, generation, failure.message ?: failure.javaClass.simpleName)
        } finally {
            if (persistedBitmap !== capture.bitmap) persistedBitmap?.recycle()
        }
    }

    private fun publishDescriptorAndPointer(
        context: Context,
        descriptor: HandoffDescriptor
    ): WriteResult = synchronized(generationLock) {
        val active = readActiveGeneration(context) ?: 0L
        if (descriptor.generation < active) {
            return@synchronized WriteResult(false, descriptor.generation, "obsolete-generation-active=$active")
        }
        val directory = storeDirectory(context)
        val descriptorFile = File(directory, descriptorFileName(descriptor.generation))
        fault(FaultPoint.BEFORE_DESCRIPTOR_WRITE)
        writeJsonAtomically(descriptorFile, descriptorToJson(descriptor))
        fault(FaultPoint.AFTER_DESCRIPTOR_PUBLISH)
        fault(FaultPoint.BEFORE_ACTIVE_POINTER_COMMIT)
        writeJsonAtomically(
            File(directory, ACTIVE_POINTER),
            JSONObject()
                .put("version", DESCRIPTOR_VERSION)
                .put("generation", descriptor.generation)
                .put("descriptor", descriptorFile.name)
        )
        pruneObsoleteGenerationFiles(directory, descriptor)
        WriteResult(true, descriptor.generation, "committed")
    }

    private fun pruneObsoleteGenerationFiles(
        directory: File,
        activeDescriptor: HandoffDescriptor
    ) {
        val activeDescriptorFileName = descriptorFileName(activeDescriptor.generation)
        val activeImageFileName = activeDescriptor.imageFileName
        directory.listFiles()?.forEach { file ->
            val name = file.name
            if (name == ACTIVE_POINTER) return@forEach
            if (managedGenerationFilePattern.matchEntire(name) == null) return@forEach
            if (name == activeDescriptorFileName) return@forEach
            if (activeImageFileName != null && name == activeImageFileName) return@forEach
            file.delete()
        }
        File(directory, "$ACTIVE_POINTER.tmp").delete()
    }

    private fun descriptorFor(
        context: Context,
        generation: Long,
        state: osrsMapPrototypeHandoffState,
        image: ImageMetadata?
    ): HandoffDescriptor {
        val resources = context.resources
        val metrics = resources.displayMetrics
        val configuration = resources.configuration
        check(configuration.orientation != Configuration.ORIENTATION_UNDEFINED)
        return HandoffDescriptor(
            version = DESCRIPTOR_VERSION,
            generation = generation,
            fragmentStateBase64 = encodeBundle(state.fragmentState),
            cameraLatitude = state.camera.latitude,
            cameraLongitude = state.camera.longitude,
            cameraZoom = state.camera.zoom,
            cameraBearing = state.camera.bearing,
            cameraTilt = state.camera.tilt,
            floor = state.floor,
            viewportWidthPx = state.viewportWidthPx,
            viewportHeightPx = state.viewportHeightPx,
            displayWidthPx = metrics.widthPixels,
            displayHeightPx = metrics.heightPixels,
            density = metrics.density,
            densityDpi = metrics.densityDpi,
            fontScale = configuration.fontScale,
            orientation = configuration.orientation,
            padding = state.padding,
            imageFileName = image?.fileName,
            imageWidthPx = image?.widthPx,
            imageHeightPx = image?.heightPx,
            imageSha256 = image?.sha256
        )
    }

    private fun descriptorToJson(descriptor: HandoffDescriptor): JSONObject {
        val image = if (descriptor.hasPreview) {
            JSONObject()
                .put("file", descriptor.imageFileName)
                .put("width_px", descriptor.imageWidthPx)
                .put("height_px", descriptor.imageHeightPx)
                .put("sha256", descriptor.imageSha256)
        } else {
            JSONObject.NULL
        }
        return JSONObject()
            .put("version", descriptor.version)
            .put("generation", descriptor.generation)
            .put("fragment_state_base64", descriptor.fragmentStateBase64)
            .put("camera", JSONObject()
                .put("latitude", descriptor.cameraLatitude)
                .put("longitude", descriptor.cameraLongitude)
                .put("zoom", descriptor.cameraZoom)
                .put("bearing", descriptor.cameraBearing)
                .put("tilt", descriptor.cameraTilt))
            .put("floor", descriptor.floor)
            .put("viewport_width_px", descriptor.viewportWidthPx)
            .put("viewport_height_px", descriptor.viewportHeightPx)
            .put("display_width_px", descriptor.displayWidthPx)
            .put("display_height_px", descriptor.displayHeightPx)
            .put("density", descriptor.density.toDouble())
            .put("density_dpi", descriptor.densityDpi)
            .put("font_scale", descriptor.fontScale.toDouble())
            .put("orientation", descriptor.orientation)
            .put("padding", JSONObject()
                .put("left_px", descriptor.padding.leftPx)
                .put("top_px", descriptor.padding.topPx)
                .put("right_px", descriptor.padding.rightPx)
                .put("bottom_px", descriptor.padding.bottomPx))
            .put("image", image)
    }

    private fun descriptorFromJson(json: JSONObject): HandoffDescriptor? = try {
        val camera = json.getJSONObject("camera")
        val padding = json.getJSONObject("padding")
        val image = json.optJSONObject("image")
        HandoffDescriptor(
            version = json.getInt("version"),
            generation = json.getLong("generation"),
            fragmentStateBase64 = json.getString("fragment_state_base64"),
            cameraLatitude = camera.getDouble("latitude"),
            cameraLongitude = camera.getDouble("longitude"),
            cameraZoom = camera.getDouble("zoom"),
            cameraBearing = camera.getDouble("bearing"),
            cameraTilt = camera.getDouble("tilt"),
            floor = json.getInt("floor"),
            viewportWidthPx = json.getInt("viewport_width_px"),
            viewportHeightPx = json.getInt("viewport_height_px"),
            displayWidthPx = json.getInt("display_width_px"),
            displayHeightPx = json.getInt("display_height_px"),
            density = json.getDouble("density").toFloat(),
            densityDpi = json.getInt("density_dpi"),
            fontScale = json.getDouble("font_scale").toFloat(),
            orientation = json.getInt("orientation"),
            padding = osrsMapPrototypePadding(
                padding.getInt("left_px"),
                padding.getInt("top_px"),
                padding.getInt("right_px"),
                padding.getInt("bottom_px")
            ),
            imageFileName = image?.getString("file"),
            imageWidthPx = image?.getInt("width_px"),
            imageHeightPx = image?.getInt("height_px"),
            imageSha256 = image?.getString("sha256")
        )
    } catch (_: Exception) {
        null
    }

    private fun loadAndValidatePreview(context: Context, descriptor: HandoffDescriptor): Bitmap? {
        val file = File(storeDirectory(context), descriptor.imageFileName ?: return null)
        if (!file.isFile || sha256(file) != descriptor.imageSha256) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            if (bitmap.width == descriptor.imageWidthPx && bitmap.height == descriptor.imageHeightPx) {
                bitmap
            } else {
                bitmap.recycle()
                null
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun scaledPreview(source: Bitmap): Bitmap {
        check(!source.isRecycled && source.width > 0 && source.height > 0)
        val targetWidth = (source.width / 2).coerceAtMost(PERSISTED_PREVIEW_MAX_WIDTH_PX)
        if (targetWidth !in 1 until source.width) return source
        val targetHeight = (source.height * (targetWidth.toDouble() / source.width))
            .toInt()
            .coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun terrainGeometryMatches(
        context: Context,
        descriptor: HandoffDescriptor,
        state: osrsMapPrototypeHandoffState
    ): Boolean {
        val current = descriptorFor(context, descriptor.generation, state, image = null)
        return abs(descriptor.cameraLatitude - current.cameraLatitude) < 1e-9 &&
            abs(descriptor.cameraLongitude - current.cameraLongitude) < 1e-9 &&
            abs(descriptor.cameraZoom - current.cameraZoom) < 1e-9 &&
            abs(descriptor.cameraBearing - current.cameraBearing) < 1e-9 &&
            abs(descriptor.cameraTilt - current.cameraTilt) < 1e-9 &&
            descriptor.floor == current.floor &&
            descriptor.viewportWidthPx == current.viewportWidthPx &&
            descriptor.viewportHeightPx == current.viewportHeightPx &&
            descriptor.displayWidthPx == current.displayWidthPx &&
            descriptor.displayHeightPx == current.displayHeightPx &&
            descriptor.densityDpi == current.densityDpi &&
            abs(descriptor.density - current.density) < 0.001f &&
            abs(descriptor.fontScale - current.fontScale) < 0.001f &&
            descriptor.orientation == current.orientation &&
            descriptor.padding == current.padding
    }

    private fun fragmentStateMatchesDescriptor(
        state: Bundle,
        descriptor: HandoffDescriptor
    ): Boolean {
        if (!state.containsKey("state_current_floor") ||
            state.getInt("state_current_floor") != descriptor.floor
        ) return false
        val semantic = state.getBundle("state_semantic_prototype") ?: return false
        val required = listOf(
            "prototype_camera_lat",
            "prototype_camera_lon",
            "prototype_camera_zoom",
            "prototype_camera_bearing",
            "prototype_camera_tilt"
        )
        if (required.any { !semantic.containsKey(it) }) return false
        return abs(semantic.getDouble("prototype_camera_lat") - descriptor.cameraLatitude) < 1e-9 &&
            abs(semantic.getDouble("prototype_camera_lon") - descriptor.cameraLongitude) < 1e-9 &&
            abs(semantic.getDouble("prototype_camera_zoom") - descriptor.cameraZoom) < 1e-9 &&
            abs(semantic.getDouble("prototype_camera_bearing") - descriptor.cameraBearing) < 1e-9 &&
            abs(semantic.getDouble("prototype_camera_tilt") - descriptor.cameraTilt) < 1e-9
    }

    private fun encodeBundle(state: Bundle): String {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(state)
            Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
        } finally {
            parcel.recycle()
        }
    }

    private fun decodeBundle(encoded: String): Bundle? {
        val parcel = Parcel.obtain()
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            parcel.readBundle(MapPrototypeStateStore::class.java.classLoader)
        } catch (_: RuntimeException) {
            null
        } finally {
            parcel.recycle()
        }
    }

    private fun readActiveGeneration(context: Context): Long? {
        return readJson(File(storeDirectory(context), ACTIVE_POINTER))
            ?.takeIf { it.optInt("version", -1) == DESCRIPTOR_VERSION }
            ?.optLong("generation", -1L)
            ?.takeIf { it > 0L }
    }

    private fun storeDirectory(context: Context): File {
        return File(context.filesDir, STORE_DIRECTORY).apply { mkdirs() }
    }

    private fun writeJsonAtomically(target: File, json: JSONObject) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        atomicReplace(temporary, target)
    }

    private fun atomicReplace(temporary: File, target: File) {
        if (temporary.renameTo(target)) return
        try {
            Os.rename(temporary.absolutePath, target.absolutePath)
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        }
    }

    private fun readJson(file: File): JSONObject? {
        if (!file.isFile) return null
        return try {
            JSONObject(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fault(point: FaultPoint) {
        faultInjectorForTesting?.invoke(point)
    }

    private fun imageFileName(generation: Long) = "generation-$generation.webp"

    private fun descriptorFileName(generation: Long) = "generation-$generation.json"

    private data class ImageMetadata(
        val fileName: String,
        val widthPx: Int,
        val heightPx: Int,
        val sha256: String
    )
}
