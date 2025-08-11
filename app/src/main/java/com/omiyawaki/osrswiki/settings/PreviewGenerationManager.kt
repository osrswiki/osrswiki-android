package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages background generation of table collapse and theme previews to eliminate loading latency.
 * 
 * This service pre-generates previews on app launch and handles smart theme switching
 * by generating new theme previews before clearing old ones, ensuring seamless transitions.
 */
object PreviewGenerationManager {
    
    private const val TAG = "PreviewGenerationMgr"
    
    // Background scope for preview generation with low priority
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Mutex to ensure only one generation process runs at a time
    private val generationMutex = Mutex()
    
    // Track generation state
    private val isInitialized = AtomicBoolean(false)
    private val currentGenerationJob = AtomicReference<Job?>(null)
    
    // Track what has been generated to avoid duplicates
    private val generatedTablePreviews = mutableSetOf<String>()
    private val generatedThemePreviews = mutableSetOf<String>()
    
    /**
     * Initialize background preview generation when Activity context is available.
     * Should be called from CustomAppearanceSettingsFragment with Activity context.
     * Safe to call multiple times - will only run once.
     */
    fun initializeBackgroundGeneration(context: Context, currentTheme: Theme) {
        if (!isInitialized.compareAndSet(false, true)) {
            Log.d(TAG, "Background generation already initialized")
            return
        }
        
        Log.d(TAG, "Starting background preview generation for theme: ${currentTheme.tag}")
        
        val job = backgroundScope.launch {
            try {
                generationMutex.withLock {
                    // Phase 1: Generate current theme previews (high priority for instant loading)
                    generateCurrentThemePreviews(context, currentTheme)
                    
                    // Phase 2: Generate alternative theme previews (low priority, nice-to-have)
                    generateAlternativeThemePreviews(context, currentTheme)
                }
                
                Log.d(TAG, "Background preview generation completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Background preview generation failed", e)
            }
        }
        
        currentGenerationJob.set(job)
    }
    
    /**
     * Handle theme changes with smart cache swapping to eliminate switch latency.
     * Generates new theme previews in background, then swaps atomically.
     */
    suspend fun handleThemeChange(context: Context, newTheme: Theme) {
        Log.d(TAG, "Handling theme change to: ${newTheme.tag}")
        
        try {
            generationMutex.withLock {
                // Generate new theme previews in background (keeping old ones available)
                generateThemeSpecificPreviews(context, newTheme)
                
                // Old previews are automatically superseded by the new cache keys
                // No need to explicitly clear - the cache system handles this
                Log.d(TAG, "Theme change completed - new previews ready")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Theme change handling failed", e)
            // Fallback: clear caches and regenerate (original behavior)
            withContext(Dispatchers.Main) {
                TablePreviewRenderer.clearCache(context)
                ThemePreviewRenderer.clearCache(context)
            }
        }
    }
    
    /**
     * Generate previews for the current theme (high priority).
     */
    private suspend fun generateCurrentThemePreviews(context: Context, currentTheme: Theme) {
        Log.d(TAG, "Phase 1: Generating current theme previews (${currentTheme.tag})")
        
        // Generate table collapse previews for current theme
        generateTablePreviews(context, currentTheme)
        
        // Generate theme preview for current theme
        generateThemePreview(context, currentTheme)
    }
    
    /**
     * Generate previews for alternative themes (low priority).
     */
    private suspend fun generateAlternativeThemePreviews(context: Context, currentTheme: Theme) {
        val alternativeTheme = when (currentTheme) {
            Theme.OSRS_LIGHT -> Theme.OSRS_DARK
            Theme.OSRS_DARK -> Theme.OSRS_LIGHT
        }
        
        Log.d(TAG, "Phase 2: Generating alternative theme previews (${alternativeTheme.tag})")
        
        // Generate table collapse previews for alternative theme
        generateTablePreviews(context, alternativeTheme)
        
        // Generate theme preview for alternative theme  
        generateThemePreview(context, alternativeTheme)
    }
    
    /**
     * Generate all previews for a specific theme (used during theme changes).
     */
    private suspend fun generateThemeSpecificPreviews(context: Context, theme: Theme) {
        Log.d(TAG, "Generating theme-specific previews for: ${theme.tag}")
        
        // Generate table collapse previews
        generateTablePreviews(context, theme)
        
        // Generate theme preview
        generateThemePreview(context, theme)
    }
    
    /**
     * Generate table collapse previews (collapsed + expanded) for a theme.
     */
    private suspend fun generateTablePreviews(context: Context, theme: Theme) {
        val collapsedKey = "table-${theme.tag}-collapsed"
        val expandedKey = "table-${theme.tag}-expanded"
        
        // Skip if already generated
        if (generatedTablePreviews.contains(collapsedKey) && 
            generatedTablePreviews.contains(expandedKey)) {
            Log.d(TAG, "Table previews for ${theme.tag} already generated, skipping")
            return
        }
        
        try {
            Log.d(TAG, "Generating table previews for theme: ${theme.tag}")
            
            // Generate collapsed state preview
            if (!generatedTablePreviews.contains(collapsedKey)) {
                TablePreviewRenderer.getPreview(context, true, theme)
                generatedTablePreviews.add(collapsedKey)
                Log.d(TAG, "Generated collapsed table preview for ${theme.tag}")
            }
            
            // Generate expanded state preview
            if (!generatedTablePreviews.contains(expandedKey)) {
                TablePreviewRenderer.getPreview(context, false, theme)
                generatedTablePreviews.add(expandedKey)
                Log.d(TAG, "Generated expanded table preview for ${theme.tag}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate table previews for ${theme.tag}", e)
        }
    }
    
    /**
     * Generate theme preview for a theme.
     */
    private suspend fun generateThemePreview(context: Context, theme: Theme) {
        val themeKey = "theme-${theme.tag}"
        
        // Skip if already generated
        if (generatedThemePreviews.contains(themeKey)) {
            Log.d(TAG, "Theme preview for ${theme.tag} already generated, skipping")
            return
        }
        
        try {
            Log.d(TAG, "Generating theme preview for: ${theme.tag}")
            
            // Generate theme preview based on theme type
            when (theme) {
                Theme.OSRS_LIGHT -> ThemePreviewRenderer.getPreview(
                    context, 
                    R.style.Theme_OSRSWiki_OSRSLight, 
                    "light"
                )
                Theme.OSRS_DARK -> ThemePreviewRenderer.getPreview(
                    context, 
                    R.style.Theme_OSRSWiki_OSRSDark, 
                    "dark"
                )
            }
            
            generatedThemePreviews.add(themeKey)
            Log.d(TAG, "Generated theme preview for ${theme.tag}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate theme preview for ${theme.tag}", e)
        }
    }
    
    /**
     * Cancel any ongoing background generation (for app lifecycle management).
     */
    fun cancelGeneration() {
        Log.d(TAG, "Cancelling background preview generation")
        currentGenerationJob.getAndSet(null)?.cancel()
    }
    
    /**
     * Reset generation state (for testing or manual cache clearing).
     */
    fun resetState() {
        Log.d(TAG, "Resetting preview generation state")
        cancelGeneration()
        generatedTablePreviews.clear()
        generatedThemePreviews.clear()
        isInitialized.set(false)
    }
    
    /**
     * Get current generation status for debugging.
     */
    fun getGenerationStatus(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized.get(),
            "generatedTablePreviews" to generatedTablePreviews.toList(),
            "generatedThemePreviews" to generatedThemePreviews.toList(),
            "hasActiveJob" to (currentGenerationJob.get()?.isActive == true)
        )
    }
}