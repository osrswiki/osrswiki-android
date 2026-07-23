package com.omiyawaki.osrswiki.settings

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume

/**
 * Manages a pool of available Activity contexts for WebView creation.
 * 
 * Since WebView requires Activity context but our preview generation runs in Application scope,
 * this pool allows Application-scoped coroutines to wait for available Activity contexts.
 */
object ActivityContextPool {
    
    private const val TAG = "ActivityContextPool"
    private const val CONTEXT_TIMEOUT_MS = 25000L // 25 seconds (increased tolerance)
    private const val RETRY_DELAY_MS = 1000L // Wait 1 second before retrying after Activity loss
    
    // Pool of available Activity contexts
    private val availableActivities = ConcurrentLinkedQueue<WeakReference<AppCompatActivity>>()
    
    // Queue of requests waiting for Activity context
    private val waitingRequests = ConcurrentLinkedQueue<kotlinx.coroutines.CancellableContinuation<AppCompatActivity?>>()
    
    // Queue of callbacks waiting for Activity context to become ready
    private val activityReadyCallbacks = ConcurrentLinkedQueue<(AppCompatActivity) -> Unit>()
    
    // Mutex for thread-safe operations
    private val poolMutex = Mutex()
    
    /**
     * Register an Activity as available for WebView creation.
     * Should be called from Activity.onResume()
     */
    suspend fun registerActivity(activity: AppCompatActivity) {
        poolMutex.withLock {
            Log.d(TAG, "Registering activity: ${activity::class.simpleName}")
            Log.i("StartupTiming", "ActivityContextPool.registerActivity() - Activity available for pool")
            
            // Remove any dead references first
            cleanupDeadReferences()
            
            // Add to pool
            availableActivities.offer(WeakReference(activity))
            
            // Fulfill any waiting requests
            val waitingRequest = waitingRequests.poll()
            waitingRequest?.let { continuation ->
                Log.d(TAG, "Fulfilling waiting request with newly registered activity")
                Log.i("StartupTiming", "ActivityContextPool fulfilling waiting preview generation request")
                continuation.resume(activity)
            }
            
            // Execute any waiting Activity-ready callbacks
            val readyCallback = activityReadyCallbacks.poll()
            readyCallback?.let { callback ->
                Log.d(TAG, "Executing activity-ready callback with newly registered activity")
                Log.i("StartupTiming", "ActivityContextPool executing activity-ready callback for deferred generation")
                try {
                    callback(activity)
                } catch (e: Exception) {
                    Log.e(TAG, "Activity-ready callback failed", e)
                }
            }
            
            Log.d(TAG, "Activity pool size: ${availableActivities.size}, waiting requests: ${waitingRequests.size}, ready callbacks: ${activityReadyCallbacks.size}")
        }
    }
    
    /**
     * Unregister an Activity when it's no longer available.
     * Should be called from Activity.onPause() or onDestroy()
     */
    suspend fun unregisterActivity(activity: AppCompatActivity) {
        poolMutex.withLock {
            Log.d(TAG, "Unregistering activity: ${activity::class.simpleName}")
            
            // Remove all references to this activity
            val iterator = availableActivities.iterator()
            while (iterator.hasNext()) {
                val ref = iterator.next()
                val refActivity = ref.get()
                if (refActivity == null || refActivity === activity) {
                    iterator.remove()
                }
            }
            
            Log.d(TAG, "Activity pool size after unregister: ${availableActivities.size}")
        }
    }
    
    /**
     * Wait for an available Activity context with timeout and resilient retry.
     * Returns null if no Activity becomes available within the timeout.
     */
    suspend fun waitForActivityContext(): AppCompatActivity? {
        return withTimeoutOrNull(CONTEXT_TIMEOUT_MS) {
            // Retry loop to handle Activity lifecycle changes during startup
            repeat(10) { attempt ->
                poolMutex.withLock {
                    // Clean up dead references
                    cleanupDeadReferences()
                    
                    // Check if we have an available activity immediately
                    while (availableActivities.isNotEmpty()) {
                        val ref = availableActivities.poll()
                        val activity = ref?.get()
                        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                            Log.d(TAG, "Providing immediate activity context: ${activity::class.simpleName}")
                            return@withTimeoutOrNull activity
                        }
                    }
                    
                    if (attempt == 0) {
                        Log.d(TAG, "No immediate activity available, waiting...")
                        Log.i("StartupTiming", "ActivityContextPool - No immediate activity available, preview generation will wait")
                    } else {
                        Log.d(TAG, "Activity context retry attempt ${attempt + 1}/10")
                    }
                }
                
                // Brief delay before retrying (helps with Activity lifecycle timing)
                if (attempt < 9) { // Don't delay on final attempt
                    delay(RETRY_DELAY_MS)
                }
            }
            
            // No immediate activity available, suspend until one becomes available
            suspendCancellableCoroutine<AppCompatActivity?> { continuation ->
                poolMutex.tryLock().let { acquired ->
                    if (acquired) {
                        try {
                            // Double-check after acquiring lock
                            while (availableActivities.isNotEmpty()) {
                                val ref = availableActivities.poll()
                                val activity = ref?.get()
                                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                                    Log.d(TAG, "Providing activity context after lock: ${activity::class.simpleName}")
                                    continuation.resume(activity)
                                    return@suspendCancellableCoroutine
                                }
                            }
                            
                            // Still no activity, add to waiting queue
                            waitingRequests.offer(continuation)
                            Log.d(TAG, "Added request to waiting queue, total waiting: ${waitingRequests.size}")
                        } finally {
                            poolMutex.unlock()
                        }
                    } else {
                        // Couldn't acquire lock, add to waiting queue anyway
                        waitingRequests.offer(continuation)
                        Log.d(TAG, "Added request to waiting queue (lock busy), total waiting: ${waitingRequests.size}")
                    }
                }
                
                continuation.invokeOnCancellation {
                    Log.d(TAG, "Activity context request was cancelled")
                    waitingRequests.remove(continuation)
                }
            }
        }
    }
    
    /**
     * Register a callback to be executed when an Activity becomes ready.
     * If an Activity is immediately available, the callback is executed synchronously.
     * Otherwise, it's queued and executed when an Activity registers.
     */
    suspend fun onActivityReady(callback: (AppCompatActivity) -> Unit) {
        poolMutex.withLock {
            Log.d(TAG, "Registering activity-ready callback")
            
            // Clean up dead references
            cleanupDeadReferences()
            
            // Check if we have an available activity immediately
            while (availableActivities.isNotEmpty()) {
                val ref = availableActivities.poll()
                val activity = ref?.get()
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    Log.d(TAG, "Executing activity-ready callback immediately with available activity")
                    Log.i("StartupTiming", "ActivityContextPool executing immediate activity-ready callback")
                    try {
                        callback(activity)
                        return // Callback executed, we're done
                    } catch (e: Exception) {
                        Log.e(TAG, "Immediate activity-ready callback failed", e)
                        return // Don't queue if callback failed
                    }
                }
            }
            
            // No immediate activity available, queue the callback
            activityReadyCallbacks.offer(callback)
            Log.d(TAG, "Queued activity-ready callback, total queued: ${activityReadyCallbacks.size}")
            Log.i("StartupTiming", "ActivityContextPool queuing activity-ready callback for deferred execution")
        }
    }
    
    /**
     * Remove dead weak references from the pool.
     * Should be called with poolMutex held.
     */
    private fun cleanupDeadReferences() {
        val iterator = availableActivities.iterator()
        var removedCount = 0
        while (iterator.hasNext()) {
            val ref = iterator.next()
            val activity = ref.get()
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                iterator.remove()
                removedCount++
            }
        }
        if (removedCount > 0) {
            Log.d(TAG, "Cleaned up $removedCount dead activity references")
        }
    }
    
    /**
     * Get current pool status for debugging.
     */
    fun getPoolStatus(): Map<String, Any> {
        return mapOf(
            "availableActivities" to availableActivities.size,
            "waitingRequests" to waitingRequests.size,
            "activityReadyCallbacks" to activityReadyCallbacks.size,
            "activeActivities" to availableActivities.mapNotNull { ref ->
                ref.get()?.let { activity ->
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity::class.simpleName
                    } else null
                }
            }
        )
    }
}