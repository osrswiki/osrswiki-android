package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import android.graphics.Bitmap
import android.os.Build
import kotlin.math.roundToInt

/**
 * Manages background generation of table collapse and theme previews to eliminate loading latency.
 * 
 * This service pre-generates previews on app launch and handles smart theme switching
 * by generating new theme previews before clearing old ones, ensuring seamless transitions.
 */
object PreviewGenerationManager {
    
    private const val TAG = "PreviewGenerationMgr"
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2000L // 2 seconds between retries
    private const val GENERATION_TIMEOUT_MS = 45000L // 45 seconds total timeout (increased)
    private const val SINGLE_PREVIEW_TIMEOUT_MS = 20000L // 20 seconds per preview (increased)
    // Removed arbitrary startup delay - we'll use Activity readiness signaling instead
    
    // Will use Application scope for generation that survives Activity lifecycle
    
    // Mutex to ensure only one generation process runs at a time
    private val generationMutex = Mutex()
    
    // Track generation state
    private val isInitialized = AtomicBoolean(false)
    
    // Track what has been generated to avoid duplicates
    private val generatedTablePreviews = mutableSetOf<String>()
    private val generatedThemePreviews = mutableSetOf<String>()
    
    /**
     * Initialize background preview generation using Application-level scope.
     * Uses scope that survives Activity lifecycle changes.
     * Safe to call multiple times - will only run once.
     * 
     * Completes only after the cache work it owns has finished.
     */
    suspend fun initializeBackgroundGeneration(context: Context, currentTheme: Theme) {
        val app = context.applicationContext as OSRSWikiApp
        val startTime = System.currentTimeMillis()

        try {
            generationMutex.withLock {
                val cacheValid = verifyThemePreviewCachesOnDisk(app)
                if (cacheValid) {
                    isInitialized.set(true)
                    Log.d(TAG, "Valid theme preview cache found, skipping generation")
                    return
                }

                isInitialized.set(true)
                Log.d(TAG, "Starting bounded theme preview generation for theme: ${currentTheme.tag}")

                withTimeout(GENERATION_TIMEOUT_MS) {
                    generateAllPreviewsUnified(context, currentTheme)
                }

                val cacheVerified = verifyThemePreviewCachesOnDisk(app)
                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Bounded preview generation completed in ${totalTime}ms; theme_cache_verified=$cacheVerified")
            }
        } catch (e: TimeoutCancellationException) {
            isInitialized.set(false)
            Log.w(TAG, "Bounded preview generation timed out after ${GENERATION_TIMEOUT_MS}ms")
            throw e
        } catch (e: Exception) {
            isInitialized.set(false)
            Log.w(TAG, "Bounded preview generation failed: ${e.message}")
            throw e
        }
    }
    
    /**
     * Handle theme changes with smart cache swapping to eliminate switch latency.
     * Generates new theme previews in background, then swaps atomically.
     */
    suspend fun handleThemeChange(app: OSRSWikiApp, newTheme: Theme) {
        Log.d(TAG, "Handling theme change to: ${newTheme.tag}")
        
        try {
            initializeBackgroundGeneration(app, newTheme)
            Log.d(TAG, "Theme change completed - new previews ready")
        } catch (e: Exception) {
            Log.e(TAG, "Theme change handling failed", e)
            // Fallback: clear caches and regenerate (original behavior)
            withContext(Dispatchers.Main) {
                TablePreviewRenderer.clearCache(app)
                ThemePreviewRenderer.clearCache(app)
            }
        }
    }
    
    /**
     * Retry wrapper that handles cancellation exceptions with exponential backoff.
     * Specifically designed to handle JobCancellationException during WebView rendering.
     */
    private suspend fun <T> withRetry(
        operationName: String,
        operation: suspend () -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                Log.d(TAG, "Executing $operationName (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS)")
                return operation()
            } catch (e: CancellationException) {
                lastException = e
                Log.w(TAG, "$operationName cancelled on attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS: ${e.message}")
                
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    val delayMs = RETRY_DELAY_MS * (attempt + 1) // Exponential backoff
                    Log.d(TAG, "Retrying $operationName in ${delayMs}ms...")
                    delay(delayMs)
                } else {
                    Log.e(TAG, "$operationName failed after $MAX_RETRY_ATTEMPTS attempts")
                    throw e
                }
            } catch (e: Exception) {
                // Non-cancellation exceptions should fail immediately
                Log.e(TAG, "$operationName failed with non-cancellation exception", e)
                throw e
            }
        }
        
        // Should never reach here, but just in case
        throw lastException ?: Exception("Unknown retry failure")
    }
    
    /**
     * Generate ALL previews using phased approach (eliminates technical debt + timing issues).
     * Phase 1: Theme previews immediately (no Activity context needed)
     * Phase 2: Table previews when Activity becomes ready (WebView requires Activity context)
     * Uses same renderers as on-demand generation, ensuring identical configurations.
     */
    private suspend fun generateAllPreviewsUnified(context: Context, currentTheme: Theme) {
        Log.d(TAG, "BOUNDED GENERATION: theme previews now, table WebViews lazy in Appearance")
        val startTime = System.currentTimeMillis()
        
        try {
            // Get OSRSWikiApp for storing results
            val app = context.applicationContext as OSRSWikiApp
            
            val themeLight = ThemePreviewRenderer.getPreview(context, R.style.Theme_OSRSWiki_OSRSLight, "light")
            val themeDark = ThemePreviewRenderer.getPreview(context, R.style.Theme_OSRSWiki_OSRSDark, "dark")

            storeThemePreviewInCache(app, themeLight, Theme.OSRS_LIGHT)
            storeThemePreviewInCache(app, themeDark, Theme.OSRS_DARK)

            val lightThemeKey = "theme-${Theme.OSRS_LIGHT.tag}"
            val darkThemeKey = "theme-${Theme.OSRS_DARK.tag}"
            generatedThemePreviews.addAll(listOf(lightThemeKey, darkThemeKey))
            
            val overallDuration = System.currentTimeMillis() - startTime
            Log.d(TAG, "BOUNDED GENERATION: Theme previews completed in ${overallDuration}ms; table previews remain lazy")
            
        } catch (e: Exception) {
            Log.w(TAG, "BOUNDED GENERATION: Failed: ${e.message}")
            throw e
        }
    }
    
    /**
     * Generate all previews for a specific theme (used during theme changes).
     * Uses the same streamlined approach as background generation.
     */
    private suspend fun generateThemeSpecificPreviews(app: OSRSWikiApp, theme: Theme) {
        initializeBackgroundGeneration(app, theme)
    }
    
    /**
     * Cancel any ongoing background generation (for app lifecycle management).
     * NOTE: Since generation is now synchronous within WorkManager, 
     * this primarily resets state for cleanup.
     */
    fun cancelGeneration() {
        Log.d(TAG, "Resetting preview generation state")
        generatedTablePreviews.clear()
        generatedThemePreviews.clear()
        isInitialized.set(false)
    }
    
    /**
     * Reset generation state (for testing or manual cache clearing).
     */
    fun resetState() {
        Log.d(TAG, "Resetting preview generation state")
        generatedTablePreviews.clear()
        generatedThemePreviews.clear()
        isInitialized.set(false)
    }
    
    /**
     * Verify that preview caches are actually written to disk and are complete.
     * This ensures WorkManager completion represents real cache durability.
     * Enhanced version that checks for specific expected cache files.
     */
    private suspend fun verifyPreviewCachesOnDisk(app: OSRSWikiApp, currentTheme: Theme): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache directories exist and are writable
                val tableCacheDir = java.io.File(app.cacheDir, "table_previews")
                val themeCacheDir = java.io.File(app.cacheDir, "theme_previews")
                
                val tableCacheDirExists = tableCacheDir.exists() && tableCacheDir.isDirectory && tableCacheDir.canRead()
                val themeCacheDirExists = themeCacheDir.exists() && themeCacheDir.isDirectory && themeCacheDir.canRead()
                
                // Count cache files in directories
                val tableCacheFileCount = if (tableCacheDirExists) {
                    tableCacheDir.listFiles()?.size ?: 0
                } else 0
                
                val themeCacheFileCount = if (themeCacheDirExists) {
                    themeCacheDir.listFiles()?.size ?: 0
                } else 0
                
                // Check for expected files - we should have at least:
                // - 4 table preview files (light/dark × collapsed/expanded)
                // - 2 theme preview files (light/dark)
                val expectedTableFiles = 4
                val expectedThemeFiles = 2
                val hasMinimumTableFiles = tableCacheFileCount >= expectedTableFiles
                val hasMinimumThemeFiles = themeCacheFileCount >= expectedThemeFiles
                
                val totalCacheFiles = tableCacheFileCount + themeCacheFileCount
                val cacheComplete = hasMinimumTableFiles && hasMinimumThemeFiles
                
                Log.i("StartupTiming", "CACHE_VERIFICATION " +
                        "table_cache_dir_exists=${tableCacheDirExists} " +
                        "theme_cache_dir_exists=${themeCacheDirExists} " +
                        "table_cache_files=${tableCacheFileCount}/${expectedTableFiles} " +
                        "theme_cache_files=${themeCacheFileCount}/${expectedThemeFiles} " +
                        "total_cache_files=${totalCacheFiles} " +
                        "cache_complete=${cacheComplete}")
                
                // Return true only if we have the expected minimum cache files
                cacheComplete
                
            } catch (e: Exception) {
                Log.e(TAG, "Cache verification failed", e)
                Log.i("StartupTiming", "CACHE_VERIFICATION_FAILED error=${e.message}")
                false
            }
        }
    }

    private suspend fun verifyThemePreviewCachesOnDisk(app: OSRSWikiApp): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val themeCacheDir = File(app.cacheDir, "theme_previews")
                val themeCacheFileCount = if (themeCacheDir.exists() && themeCacheDir.isDirectory && themeCacheDir.canRead()) {
                    themeCacheDir.listFiles()?.size ?: 0
                } else {
                    0
                }
                themeCacheFileCount >= 2
            } catch (e: Exception) {
                Log.w(TAG, "Theme cache verification failed: ${e.message}")
                false
            }
        }
    }

    /**
     * Get current generation status for debugging.
     */
    fun getGenerationStatus(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized.get(),
            "generatedTablePreviews" to generatedTablePreviews.toList(),
            "generatedThemePreviews" to generatedThemePreviews.toList(),
            "generationType" to "bounded_lazy_preview_generation"
        )
    }
    
    /**
     * Stores table preview in cache using same format as TablePreviewRenderer for UI compatibility
     */
    private suspend fun storeTablePreviewInCache(
        app: OSRSWikiApp,
        bitmap: Bitmap,
        theme: Theme,
        collapsed: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "table-preview-${theme.tag}-${if (collapsed) "collapsed" else "expanded"}"
            val cacheDir = File(app.cacheDir, "table_previews").apply { mkdirs() }
            val cachedFile = File(cacheDir, "$cacheKey.webp")
            
            // Save to disk cache
            cachedFile.outputStream().use { stream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 100, stream)
                }
            }
            
            Log.d(TAG, "Stored table preview in cache: $cacheKey")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to store table preview in cache: ${e.message}")
        }
    }
    
    /**
     * Stores theme preview in cache using same format as ThemePreviewRenderer for UI compatibility
     */
    private suspend fun storeThemePreviewInCache(
        app: OSRSWikiApp,
        bitmap: Bitmap,
        theme: Theme
    ) = withContext(Dispatchers.IO) {
        try {
            // Use same cache key format as ThemePreviewRenderer
            val dm = app.resources.displayMetrics
            val (width, height) = getAppContentBounds(app)
            val density = dm.densityDpi
            val orientation = if (width > height) "landscape" else "portrait"
            val configId = "${width}x${height}-${density}dpi-${orientation}"
            val themeKey = theme.tag.replace("osrs_", "") // "light" or "dark"
            val fullCacheKey = "$themeKey-$configId"
            
            val cacheDir = File(app.cacheDir, "theme_previews").apply { mkdirs() }
            val cacheKey = "v${com.omiyawaki.osrswiki.BuildConfig.VERSION_CODE}-$fullCacheKey.webp"
            val cachedFile = File(cacheDir, cacheKey)
            
            // Save to disk cache
            cachedFile.outputStream().use { stream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 100, stream)
                }
            }
            
            Log.d(TAG, "Stored theme preview in cache: $fullCacheKey")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to store theme preview in cache: ${e.message}")
        }
    }
    
    /**
     * Gets app content bounds (same logic as ThemePreviewRenderer)
     */
    private fun getAppContentBounds(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars()
            )
            val bounds = metrics.bounds
            val contentWidth = bounds.width() - insets.left - insets.right
            val contentHeight = bounds.height() - insets.top - insets.bottom
            Pair(contentWidth, contentHeight)
        } else {
            val displayMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val fullWidth = displayMetrics.widthPixels
            val fullHeight = displayMetrics.heightPixels
            val density = displayMetrics.density
            val isLandscape = fullWidth > fullHeight
            
            if (isLandscape) {
                val estimatedSystemUIWidth = (24 * density).roundToInt()
                val estimatedSystemUIHeight = (48 * density).roundToInt()
                Pair(fullWidth - estimatedSystemUIWidth, fullHeight - estimatedSystemUIHeight)
            } else {
                val estimatedSystemUIHeight = ((24 + 48) * density).roundToInt()
                Pair(fullWidth, fullHeight - estimatedSystemUIHeight)
            }
        }
    }
    
    /**
     * Retained for NewsFragment compatibility. Preview generation is lazy and no longer
     * waits for home load callbacks.
     */
    fun onHomePageLoaded(app: OSRSWikiApp) {
        Log.d(TAG, "Home page loaded; preview generation remains lazy")
    }
}
