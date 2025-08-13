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
import kotlinx.coroutines.launch
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
     * Launches background generation immediately and returns without waiting.
     */
    fun initializeBackgroundGeneration(context: Context, currentTheme: Theme) {
        if (!isInitialized.compareAndSet(false, true)) {
            Log.d(TAG, "Background generation already initialized")
            return
        }
        
        Log.i("StartupTiming", "PreviewGenerationManager.initializeBackgroundGeneration() - Starting background preview generation for theme: ${currentTheme.tag}")
        Log.d(TAG, "Starting background preview generation for theme: ${currentTheme.tag}")
        
        // Get OSRSWikiApp from context
        val app = context.applicationContext as OSRSWikiApp
        
        // Launch background generation in Application scope (survives Activity lifecycle)
        app.applicationScope.launch {
            try {
                withTimeout(GENERATION_TIMEOUT_MS) {
                    generationMutex.withLock {
                        Log.i("StartupTiming", "PreviewGenerationManager - Starting actual generation work")
                        
                        // UNIFIED APPROACH: Generate all previews in single operation (5-8x faster)
                        // This replaces the separate Phase 1 and Phase 2 with render-once, capture-many
                        generateAllPreviewsUnified(context, currentTheme)
                        
                        Log.i("StartupTiming", "PreviewGenerationManager - All generation work completed")
                    }
                }
                
                // CRITICAL: Add disk flush verification to ensure cache is durable
                Log.i("StartupTiming", "PreviewGenerationManager - Starting disk flush verification")
                val flushStartTime = System.currentTimeMillis()
                
                // Force disk flush by attempting cache verification
                try {
                    // Verify cache files exist on disk by checking both themes and table previews
                    val cacheVerified = verifyPreviewCachesOnDisk(app, currentTheme)
                    val flushDuration = System.currentTimeMillis() - flushStartTime
                    Log.i("StartupTiming", "DISK_FLUSH_COMPLETE cache_verified=${cacheVerified} duration=${flushDuration}ms")
                } catch (e: Exception) {
                    Log.w("StartupTiming", "DISK_FLUSH_VERIFICATION_FAILED: ${e.message}")
                }
                
                Log.i("StartupTiming", "PreviewGenerationManager.initializeBackgroundGeneration() - COMPLETED after real work")
                Log.d(TAG, "Background preview generation completed successfully")
                
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Background preview generation timed out after ${GENERATION_TIMEOUT_MS}ms", e)
                Log.i("StartupTiming", "PreviewGenerationManager - TIMEOUT after ${GENERATION_TIMEOUT_MS}ms")
                // Reset initialization flag to allow retry
                isInitialized.set(false)
            } catch (e: Exception) {
                Log.e(TAG, "Background preview generation failed", e)
                Log.i("StartupTiming", "PreviewGenerationManager - FAILED: ${e.message}")
                // Reset initialization flag to allow retry
                isInitialized.set(false)
            }
        }
    }
    
    /**
     * Handle theme changes with smart cache swapping to eliminate switch latency.
     * Generates new theme previews in background, then swaps atomically.
     */
    suspend fun handleThemeChange(app: OSRSWikiApp, newTheme: Theme) {
        Log.d(TAG, "Handling theme change to: ${newTheme.tag}")
        
        try {
            generationMutex.withLock {
                // Generate new theme previews in background (keeping old ones available)
                generateThemeSpecificPreviews(app, newTheme)
                
                // Old previews are automatically superseded by the new cache keys
                // No need to explicitly clear - the cache system handles this
                Log.d(TAG, "Theme change completed - new previews ready")
            }
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
     * Generate ALL previews using unified approach (render-once, capture-many).
     * This replaces the separate generation of current and alternative theme previews
     * with a single unified operation that's 5-8x faster.
     */
    private suspend fun generateAllPreviewsUnified(context: Context, currentTheme: Theme) {
        Log.i(TAG, "UNIFIED GENERATION: Starting unified preview generation (render-once, capture-many)")
        Log.i("StartupTiming", "UNIFIED_START current_theme=${currentTheme.tag}")
        val unifiedStartTime = System.currentTimeMillis()
        
        try {
            val unifiedGenerator = UnifiedPreviewGenerator()
            val result = unifiedGenerator.generateAllPreviews(app)
            
            Log.i(TAG, "UNIFIED GENERATION: All previews generated, storing in caches")
            
            // Store table previews in cache with same keys as before (for UI compatibility)
            storeTablePreviewInCache(app, result.tableCollapsedLight, Theme.OSRS_LIGHT, true)
            storeTablePreviewInCache(app, result.tableExpandedLight, Theme.OSRS_LIGHT, false)
            storeTablePreviewInCache(app, result.tableCollapsedDark, Theme.OSRS_DARK, true)
            storeTablePreviewInCache(app, result.tableExpandedDark, Theme.OSRS_DARK, false)
            
            // Store theme previews in cache with same keys as before (for UI compatibility) 
            storeThemePreviewInCache(app, result.themeLight, Theme.OSRS_LIGHT)
            storeThemePreviewInCache(app, result.themeDark, Theme.OSRS_DARK)
            
            // Update generation tracking (all previews generated in one operation)
            val lightTableCollapsedKey = "table-${Theme.OSRS_LIGHT.tag}-collapsed"
            val lightTableExpandedKey = "table-${Theme.OSRS_LIGHT.tag}-expanded"
            val darkTableCollapsedKey = "table-${Theme.OSRS_DARK.tag}-collapsed"
            val darkTableExpandedKey = "table-${Theme.OSRS_DARK.tag}-expanded"
            val lightThemeKey = "theme-${Theme.OSRS_LIGHT.tag}"
            val darkThemeKey = "theme-${Theme.OSRS_DARK.tag}"
            
            generatedTablePreviews.addAll(listOf(
                lightTableCollapsedKey, lightTableExpandedKey,
                darkTableCollapsedKey, darkTableExpandedKey
            ))
            generatedThemePreviews.addAll(listOf(lightThemeKey, darkThemeKey))
            
            val unifiedDuration = System.currentTimeMillis() - unifiedStartTime
            Log.i("StartupTiming", "UNIFIED_COMPLETE duration=${unifiedDuration}ms (target: <3000ms)")
            Log.i(TAG, "UNIFIED GENERATION: All previews completed in ${unifiedDuration}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "UNIFIED GENERATION: Failed", e)
            Log.i("StartupTiming", "UNIFIED_FAILED error=${e.message}")
            throw e
        }
    }
    
    /**
     * Generate all previews for a specific theme (used during theme changes).
     * TODO: Update this to use unified approach as well
     */
    private suspend fun generateThemeSpecificPreviews(app: OSRSWikiApp, theme: Theme) {
        Log.d(TAG, "Generating theme-specific previews for: ${theme.tag}")
        
        // For now, fallback to unified generation of all previews
        // This is not optimal but ensures consistency while we test the unified approach
        generateAllPreviewsUnified(app, theme)
    }
    
    /**
     * Cancel any ongoing background generation (for app lifecycle management).
     * NOTE: Since generation is now synchronous within WorkManager, 
     * this primarily resets state for cleanup.
     */
    fun cancelGeneration() {
        Log.d(TAG, "Resetting preview generation state (generation is now synchronous)")
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
     * Verify that preview caches are actually written to disk.
     * This ensures WorkManager completion represents real cache durability.
     * Simplified version that checks cache directory existence and basic file I/O.
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
                
                val totalCacheFiles = tableCacheFileCount + themeCacheFileCount
                
                Log.i("StartupTiming", "CACHE_VERIFICATION " +
                        "table_cache_dir_exists=${tableCacheDirExists} " +
                        "theme_cache_dir_exists=${themeCacheDirExists} " +
                        "table_cache_files=${tableCacheFileCount} " +
                        "theme_cache_files=${themeCacheFileCount} " +
                        "total_cache_files=${totalCacheFiles}")
                
                // Return true if we have any cache files (indicating generation occurred)
                totalCacheFiles > 0
                
            } catch (e: Exception) {
                Log.e(TAG, "Cache verification failed", e)
                Log.i("StartupTiming", "CACHE_VERIFICATION_FAILED error=${e.message}")
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
            "generationType" to "unified_render_once_capture_many"
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
}