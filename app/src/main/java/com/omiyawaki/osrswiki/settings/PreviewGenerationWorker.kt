package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.theme.Theme
import kotlinx.coroutines.runBlocking

/**
 * WorkManager worker for background preview generation.
 * Runs from Application.onCreate() to ensure previews are ready before user navigation.
 * 
 * This worker eliminates race conditions by:
 * - Starting generation immediately on app launch
 * - Running independently of activity lifecycle
 * - Persisting through activity recreation/theme changes
 * - Providing robust retry logic for failures
 */
class PreviewGenerationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "PreviewWorker"
        const val WORK_NAME = "preview_generation"
        const val KEY_CURRENT_THEME = "current_theme"
        
        /**
         * Create input data for the worker.
         */
        fun createInputData(currentTheme: Theme): Data {
            return Data.Builder()
                .putString(KEY_CURRENT_THEME, currentTheme.tag)
                .build()
        }
    }

    override fun doWork(): Result {
        Log.i("StartupTiming", "PreviewGenerationWorker.doWork() - Starting background generation")
        Log.d(TAG, "Starting background preview generation in WorkManager")
        
        return try {
            val app = applicationContext as OSRSWikiApp
            val currentThemeTag = inputData.getString(KEY_CURRENT_THEME) ?: "osrs_light"
            val currentTheme = when (currentThemeTag) {
                "osrs_dark" -> Theme.OSRS_DARK
                else -> Theme.OSRS_LIGHT
            }
            
            Log.d(TAG, "Current theme for generation: ${currentTheme.tag}")
            
            // Use runBlocking to run the suspend functions in WorkManager context
            runBlocking {
                try {
                    // Wait for app dependencies to be ready
                    waitForAppDependencies(app)
                    
                    // CRITICAL FIX: Now properly awaits the actual generation completion
                    // instead of just launching work and returning immediately
                    Log.i("StartupTiming", "PreviewGenerationWorker - About to call initializeBackgroundGeneration")
                    PreviewGenerationManager.initializeBackgroundGeneration(app, currentTheme)
                    Log.i("StartupTiming", "PreviewGenerationWorker - initializeBackgroundGeneration returned, work is complete")
                    
                    Log.i("StartupTiming", "PreviewGenerationWorker completed successfully")
                    Log.d(TAG, "Background preview generation completed successfully")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Background preview generation failed in WorkManager", e)
                    Log.i("StartupTiming", "PreviewGenerationWorker failed: ${e.message}")
                    throw e
                }
            }
            
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "PreviewGenerationWorker failed", e)
            // Return retry for recoverable failures, failure for permanent issues
            if (shouldRetry(e)) {
                Log.w(TAG, "Retrying preview generation due to recoverable error")
                Result.retry()
            } else {
                Log.e(TAG, "Preview generation failed permanently")
                Result.failure()
            }
        }
    }
    
    /**
     * Wait for app dependencies to be fully initialized before starting preview generation.
     * This prevents race conditions with database/repository initialization.
     */
    private suspend fun waitForAppDependencies(app: OSRSWikiApp, maxWaitMs: Long = 10000L) {
        val startTime = System.currentTimeMillis()
        
        while (!app.isRepositoriesInitialized) {
            val elapsedMs = System.currentTimeMillis() - startTime
            if (elapsedMs > maxWaitMs) {
                throw Exception("Timed out waiting for app dependencies to initialize")
            }
            
            Log.d(TAG, "Waiting for app dependencies to initialize... (${elapsedMs}ms)")
            kotlinx.coroutines.delay(100) // Check every 100ms
        }
        
        Log.d(TAG, "App dependencies ready for preview generation")
    }
    
    /**
     * Determine if an error is recoverable and should trigger a retry.
     */
    private fun shouldRetry(error: Exception): Boolean {
        return when {
            error.message?.contains("timeout", ignoreCase = true) == true -> true
            error.message?.contains("network", ignoreCase = true) == true -> true
            error.message?.contains("WebView", ignoreCase = true) == true -> true
            error is kotlinx.coroutines.CancellationException -> true
            else -> false
        }
    }
}