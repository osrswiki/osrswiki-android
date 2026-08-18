package com.omiyawaki.osrswiki.page

import android.view.Gravity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Axis-locks an article pointer sequence the way DrawerLayout / native back
 * gestures do: only strongly horizontal movement becomes a swipe, and once
 * locked the axis cannot flip into a vertical scroll.
 */
internal class osrsArticleInteractiveSwipe(
    private val touchSlop: Int,
    private val horizontalDominance: Float = HORIZONTAL_DOMINANCE
) {
    enum class Axis { BACK, CONTENTS }

    var axis: Axis? = null
        private set
    var locked: Boolean = false
        private set
    var isTracking: Boolean = false
        private set
    var contentsOpenAtStart: Boolean = false
        private set

    fun reset() {
        axis = null
        locked = false
        isTracking = false
        contentsOpenAtStart = false
    }

    /**
     * @return the locked axis once the pointer has travelled far enough to
     * decide, or null while still inside slop / after a vertical disqualify.
     */
    fun onMove(dx: Float, dy: Float, contentsOpen: Boolean = false): Axis? {
        if (locked) {
            return axis
        }
        if (hypot(dx.toDouble(), dy.toDouble()) < touchSlop) {
            return null
        }
        locked = true
        if (abs(dx) < abs(dy) * horizontalDominance) {
            axis = null
            isTracking = false
            return null
        }
        contentsOpenAtStart = contentsOpen
        axis = if (contentsOpen || dx <= 0f) Axis.CONTENTS else Axis.BACK
        isTracking = true
        return axis
    }

    fun progress(dx: Float, span: Float): Float {
        val width = span.coerceAtLeast(1f)
        return when (axis) {
            Axis.BACK -> (dx / width).coerceIn(0f, 1f)
            Axis.CONTENTS -> contentsProgress(dx, width, contentsOpenAtStart)
            null -> 0f
        }
    }

    fun shouldCommit(dx: Float, velocityX: Float, span: Float): Boolean {
        val current = progress(dx, span)
        return when (axis) {
            Axis.BACK -> current >= COMMIT_PROGRESS || velocityX >= COMMIT_VELOCITY
            Axis.CONTENTS -> shouldCommitContents(current, velocityX, contentsOpenAtStart)
            null -> false
        }
    }

    fun gravity(): Int? = when (axis) {
        Axis.BACK -> Gravity.START
        Axis.CONTENTS -> Gravity.END
        null -> null
    }

    companion object {
        const val HORIZONTAL_DOMINANCE = 1.75f
        const val COMMIT_PROGRESS = 0.35f
        const val COMMIT_VELOCITY = 800f
        const val CONTENTS_DRAWER_WIDTH_DP = 280f
        const val BACK_PREVIEW_PARALLAX = 0f

        const val SETTLE_COAST_DP_PER_SEC = 280f
        const val SETTLE_MIN_DURATION_MS = 120L
        const val SETTLE_MAX_DURATION_MS = 800L
        const val CONTENTS_PROGRAMMATIC_DURATION_MS = 240L
        const val CONTENTS_PROGRAMMATIC_VELOCITY_DP_PER_SEC = 40f

        fun remainingPx(progress: Float, distancePx: Float): Float {
            return (1f - progress.coerceIn(0f, 1f)) * distancePx.coerceAtLeast(1f)
        }

        fun remainingCommitDurationMs(
            progress: Float,
            velocityPxPerSec: Float,
            distancePx: Float,
            density: Float = 1f
        ): Long {
            val remaining = remainingPx(progress, distancePx)
            val densitySafe = density.coerceAtLeast(0.5f)
            val remainingDp = remaining / densitySafe
            val velocityDp = abs(velocityPxPerSec) / densitySafe
            val speedDp = maxOf(velocityDp, SETTLE_COAST_DP_PER_SEC)
            return (remainingDp / speedDp * 1000f).toLong()
                .coerceIn(SETTLE_MIN_DURATION_MS, SETTLE_MAX_DURATION_MS)
        }

        /**
         * Contents button / scrim-tap / TOC-row closes are not a finger-release
         * coast. Use a short drawer duration so Android matches iOS spring feel.
         */
        fun contentsToggleDurationMs(
            velocityPxPerSec: Float,
            distancePx: Float,
            density: Float = 1f
        ): Long {
            val densitySafe = density.coerceAtLeast(0.5f)
            val velocityDp = abs(velocityPxPerSec) / densitySafe
            if (velocityDp <= CONTENTS_PROGRAMMATIC_VELOCITY_DP_PER_SEC) {
                return CONTENTS_PROGRAMMATIC_DURATION_MS
            }
            return remainingCommitDurationMs(
                progress = 0f,
                velocityPxPerSec = velocityPxPerSec,
                distancePx = distancePx,
                density = density
            )
        }

        fun settleInterpolator(
            @Suppress("UNUSED_PARAMETER") velocityPxPerSec: Float,
            @Suppress("UNUSED_PARAMETER") remainingPx: Float,
            @Suppress("UNUSED_PARAMETER") durationMs: Long
        ): android.view.animation.Interpolator {
            // Duration already matches remaining travel / release speed. The
            // curve only decelerates to rest so a finger-up does not dump the
            // leftover distance in the last few frames.
            return osrsSettleDecelerateInterpolator()
        }

        fun contentsPeekTranslationX(drawerWidth: Float, progress: Float): Float {
            val width = drawerWidth.coerceAtLeast(1f)
            return (1f - progress.coerceIn(0f, 1f)) * width
        }

        fun contentsProgress(dx: Float, span: Float, contentsOpenAtStart: Boolean): Float {
            val width = span.coerceAtLeast(1f)
            return if (contentsOpenAtStart) {
                (1f - dx / width).coerceIn(0f, 1f)
            } else {
                ((-dx) / width).coerceIn(0f, 1f)
            }
        }

        fun shouldCommitContents(
            progress: Float,
            velocityX: Float,
            contentsOpenAtStart: Boolean
        ): Boolean {
            return if (contentsOpenAtStart) {
                progress <= (1f - COMMIT_PROGRESS) || velocityX >= COMMIT_VELOCITY
            } else {
                progress >= COMMIT_PROGRESS || velocityX <= -COMMIT_VELOCITY
            }
        }
    }
}

internal class osrsSettleDecelerateInterpolator(
    private val factor: Float = 1.15f
) : android.view.animation.Interpolator {
    override fun getInterpolation(input: Float): Float {
        val remaining = 1f - input.coerceIn(0f, 1f)
        return 1f - remaining.pow(factor)
    }
}
