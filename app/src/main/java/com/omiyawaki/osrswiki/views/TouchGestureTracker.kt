package com.omiyawaki.osrswiki.views

import android.util.Log
import com.omiyawaki.osrswiki.BuildConfig
import kotlin.math.abs

/**
 * Tracks raw finger movements to determine user intent, independent of scroll position changes.
 * This eliminates phantom triggers caused by layout changes and animation artifacts.
 * 
 * Enhanced version supporting both:
 * - Continuous proportional positioning during touch movement
 * - Discrete gesture detection for completion handling
 */
class TouchGestureTracker {
    
    private var isTracking = false
    private var startY = 0f
    private var currentY = 0f
    private var totalMovement = 0f
    private var lastY = 0f
    private var gestureDirection = Direction.NONE
    private var movementHistory = ArrayDeque<Movement>(HISTORY_SIZE)
    
    private data class Movement(val deltaY: Float, val timestamp: Long)
    
    enum class Direction {
        NONE, UP, DOWN
    }
    
    data class GestureResult(
        val direction: Direction,
        val totalDistance: Float,
        val averageVelocity: Float,
        val isSignificant: Boolean
    )
    
    data class ContinuousState(
        val totalDelta: Float,          // Total movement from start (negative = up, positive = down)
        val instantDelta: Float,        // Movement since last update
        val direction: Direction,       // Current gesture direction
        val velocity: Float,            // Current velocity (pixels/second)
        val isActive: Boolean          // Whether gesture is active
    )
    
    /**
     * Start tracking a new touch gesture
     */
    fun onTouchDown(y: Float) {
        isTracking = true
        startY = y
        currentY = y
        lastY = y
        totalMovement = 0f
        gestureDirection = Direction.NONE
        movementHistory.clear()
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Gesture tracking started at y=$y")
        }
    }
    
    /**
     * Update gesture tracking with touch movement
     * Returns continuous state for immediate proportional positioning
     */
    fun onTouchMove(y: Float): ContinuousState {
        if (!isTracking) {
            return ContinuousState(0f, 0f, Direction.NONE, 0f, false)
        }
        
        val deltaY = y - lastY
        val totalDelta = y - startY
        currentY = y
        totalMovement += abs(deltaY)
        
        // Track movement history for velocity calculation
        movementHistory.addLast(Movement(deltaY, System.currentTimeMillis()))
        if (movementHistory.size > HISTORY_SIZE) {
            movementHistory.removeFirst()
        }
        
        // Determine primary gesture direction with responsive threshold
        gestureDirection = when {
            totalDelta > DIRECTION_THRESHOLD -> Direction.DOWN
            totalDelta < -DIRECTION_THRESHOLD -> Direction.UP
            else -> Direction.NONE
        }
        
        val currentVelocity = calculateAverageVelocity()
        
        lastY = y
        
        val state = ContinuousState(
            totalDelta = totalDelta,
            instantDelta = deltaY,
            direction = gestureDirection,
            velocity = currentVelocity,
            isActive = true
        )
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Touch move: y=$y, deltaY=$deltaY, totalDelta=$totalDelta, direction=$gestureDirection, velocity=${String.format("%.1f", currentVelocity)}")
        }
        
        return state
    }
    
    /**
     * Complete gesture tracking and return analysis
     */
    fun onTouchUp(): GestureResult {
        if (!isTracking) {
            return GestureResult(Direction.NONE, 0f, 0f, false)
        }
        
        val totalDelta = currentY - startY
        val averageVelocity = calculateAverageVelocity()
        val isSignificant = isGestureSignificant(totalDelta, averageVelocity)
        
        val result = GestureResult(
            direction = gestureDirection,
            totalDistance = abs(totalDelta),
            averageVelocity = averageVelocity,
            isSignificant = isSignificant
        )
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Gesture complete: $result")
        }
        
        isTracking = false
        return result
    }
    
    /**
     * Get current gesture state without ending tracking
     */
    fun getCurrentGesture(): GestureResult? {
        if (!isTracking) return null
        
        val totalDelta = currentY - startY
        val averageVelocity = calculateAverageVelocity()
        val isSignificant = isGestureSignificant(totalDelta, averageVelocity)
        
        return GestureResult(
            direction = gestureDirection,
            totalDistance = abs(totalDelta),
            averageVelocity = averageVelocity,
            isSignificant = isSignificant
        )
    }
    
    /**
     * Get current continuous state without ending tracking
     */
    fun getCurrentState(): ContinuousState? {
        if (!isTracking) return null
        
        val totalDelta = currentY - startY
        val currentVelocity = calculateAverageVelocity()
        
        return ContinuousState(
            totalDelta = totalDelta,
            instantDelta = 0f, // No instant delta when just querying
            direction = gestureDirection,
            velocity = currentVelocity,
            isActive = true
        )
    }
    
    /**
     * Check if we're currently tracking a gesture
     */
    fun isActivelyTracking(): Boolean = isTracking
    
    /**
     * Cancel current gesture tracking
     */
    fun cancelTracking() {
        if (isTracking && BuildConfig.DEBUG) {
            Log.d(TAG, "Gesture tracking cancelled")
        }
        isTracking = false
        movementHistory.clear()
    }
    
    private fun calculateAverageVelocity(): Float {
        if (movementHistory.size < 2) return 0f
        
        val recentMovements = movementHistory.takeLast(VELOCITY_SAMPLE_SIZE)
        if (recentMovements.size < 2) return 0f
        
        val totalDelta = recentMovements.sumOf { it.deltaY.toDouble() }.toFloat()
        val timeDelta = recentMovements.last().timestamp - recentMovements.first().timestamp
        
        return if (timeDelta > 0) {
            totalDelta / timeDelta.toFloat() * 1000f // pixels per second
        } else {
            0f
        }
    }
    
    private fun isGestureSignificant(totalDelta: Float, velocity: Float): Boolean {
        // Gesture is significant if it has sufficient distance OR velocity
        val hasSignificantDistance = abs(totalDelta) >= MIN_GESTURE_DISTANCE
        val hasSignificantVelocity = abs(velocity) >= MIN_GESTURE_VELOCITY
        
        return hasSignificantDistance || hasSignificantVelocity
    }
    
    companion object {
        private const val TAG = "TouchGestureTracker"
        
        // Gesture detection thresholds (refined for responsiveness from commit f4fa86d)
        private const val DIRECTION_THRESHOLD = 5f         // Minimum movement to determine direction (reduced from 20f)
        private const val MIN_GESTURE_DISTANCE = 1f        // Minimum total distance for significant gesture (reduced from 50f)
        private const val MIN_GESTURE_VELOCITY = 200f      // Minimum velocity (pixels/second) for significant gesture
        
        // Movement tracking
        private const val HISTORY_SIZE = 10                // Number of movements to track
        private const val VELOCITY_SAMPLE_SIZE = 5         // Recent movements for velocity calculation
    }
}