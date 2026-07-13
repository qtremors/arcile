package dev.qtremors.arcile.core.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import kotlinx.coroutines.isActive

// ──────────────────────────────────────────────────────────────────────
// Timing & Smoothing Constants
// ──────────────────────────────────────────────────────────────────────

object ProgressConstants {
    /** Operations completing faster than this never show a progress pill. */
    const val UI_DEBOUNCE_DELAY_MS = 300L

    /** Once visible, the pill stays on-screen for at least this long. */
    const val MIN_DISPLAY_DURATION_MS = 800L

    /** Maximum progress velocity per second — prevents jarring teleports. */
    const val MAX_VELOCITY_PER_SEC = 0.40f

    /** Exponential catch-up factor — higher = snappier, lower = smoother. */
    const val SMOOTHING_FACTOR = 8.0f

    /** Minimum velocity (per second) so progress never stalls visually. */
    const val MIN_VELOCITY_PER_SEC = 0.01f

    /** Faster minimum velocity when operation is complete, to reach 1.0 quickly. */
    const val COMPLETION_MIN_VELOCITY_PER_SEC = 0.50f

    /** Convergence threshold — snap to target when distance is negligible. */
    const val CONVERGENCE_EPSILON = 0.001f
}

// ──────────────────────────────────────────────────────────────────────
// Smoothed Progress State
// ──────────────────────────────────────────────────────────────────────

/**
 * Holds the decoupled progress state used by the frame-clock animation loop.
 *
 * [targetProgress] is driven by raw filesystem callbacks.
 * [displayedProgress] is what the UI actually renders — it is NEVER directly
 * assigned from [targetProgress] during normal flow; instead a continuous frame
 * loop interpolates it toward the target with velocity clamping and exponential
 * smoothing.
 *
 * When the operation completes ([markComplete]), [displayedProgress] is
 * instantly snapped to 1.0 so the user gets immediate visual confirmation.
 */
@Stable
class SmoothedProgressState {
    /** Raw target progress from filesystem callbacks (0.0 → 1.0). */
    var targetProgress by mutableFloatStateOf(0f)
        internal set

    /** The value the UI should render. Driven exclusively by the frame loop. */
    var displayedProgress by mutableFloatStateOf(0f)
        internal set

    /** Whether the underlying operation has reached a terminal state. */
    var isComplete by mutableStateOf(false)
        internal set

    /** The terminal status — null while the operation is still in progress. */
    var completionStatus by mutableStateOf<OperationCompletionStatus?>(null)
        internal set

    /** Whether the displayed animation has finished (post-completion hold elapsed). */
    var isAnimationFinished by mutableStateOf(false)
        internal set

    /** Timestamp (millis) when the operation started. */
    var operationStartTime by mutableLongStateOf(0L)
        internal set

    /** Whether the pill UI should be visible (respecting debounce + min duration). */
    var isVisible by mutableStateOf(false)
        internal set

    /**
     * Update the raw target progress from a filesystem callback.
     * Enforces monotonically increasing values to prevent backward jumps.
     */
    fun updateTarget(newTarget: Float) {
        val clamped = newTarget.coerceIn(0f, 1f)
        if (clamped > targetProgress) {
            targetProgress = clamped
        }
    }

    /**
     * Signal that the operation has completed.
     * Instantly snaps [displayedProgress] to 1.0 so the user sees the
     * bar fill completely without waiting for the interpolation to catch up.
     */
    fun markComplete(status: OperationCompletionStatus = OperationCompletionStatus.SUCCESS) {
        isComplete = true
        targetProgress = 1f
        displayedProgress = 1f
        completionStatus = status
    }

    /** Reset all state for a new operation. */
    fun reset(startTime: Long = System.currentTimeMillis()) {
        targetProgress = 0f
        displayedProgress = 0f
        isComplete = false
        completionStatus = null
        isAnimationFinished = false
        operationStartTime = startTime
        isVisible = false
    }
}

// ──────────────────────────────────────────────────────────────────────
// Composable Frame-Clock Loop
// ──────────────────────────────────────────────────────────────────────

/**
 * Creates and remembers a [SmoothedProgressState] that runs a continuous
 * frame-clock animation loop, interpolating [SmoothedProgressState.displayedProgress]
 * toward [SmoothedProgressState.targetProgress].
 *
 * The loop:
 * - Uses exponential decay to compute each frame's step.
 * - Clamps maximum velocity per frame to prevent visual jumps.
 * - Enforces a minimum velocity so progress never appears frozen.
 * - Manages pill visibility with debounce and minimum display duration.
 *
 * When an operation completes, [displayedProgress] is instantly snapped to 1.0.
 * The loop then holds the full-fill state for [ProgressConstants.MIN_DISPLAY_DURATION_MS]
 * before signaling [SmoothedProgressState.isAnimationFinished].
 *
 * The returned state object can be updated from filesystem callbacks via
 * [SmoothedProgressState.updateTarget] and [SmoothedProgressState.markComplete].
 */
@Composable
fun rememberSmoothedProgress(): SmoothedProgressState {
    val state = remember { SmoothedProgressState() }

    // Frame-clock animation loop — runs whenever there is an active operation
    LaunchedEffect(state.isComplete, state.targetProgress) {
        // Nothing to animate if target is 0 and not complete
        if (state.targetProgress <= 0f && !state.isComplete) return@LaunchedEffect

        // If already complete, displayedProgress was snapped to 1.0 in markComplete().
        // We only need to wait out the minimum display duration.
        if (state.isComplete) {
            val now = System.currentTimeMillis()
            val elapsed = now - state.operationStartTime

            if (elapsed < ProgressConstants.UI_DEBOUNCE_DELAY_MS) {
                // Ultra-fast operation that completed before debounce — never show
                state.isAnimationFinished = true
                return@LaunchedEffect
            }

            // Ensure the pill is visible
            state.isVisible = true

            // Hold the full-fill for the remaining minimum display time
            val remaining = ProgressConstants.MIN_DISPLAY_DURATION_MS - elapsed
            if (remaining > 0) {
                kotlinx.coroutines.delay(remaining)
            }
            state.isAnimationFinished = true
            return@LaunchedEffect
        }

        // ── Normal in-progress animation loop ──
        var lastFrameTime = withInfiniteAnimationFrameMillis { it }

        while (isActive) {
            withInfiniteAnimationFrameMillis { frameTime ->
                val dtSec = ((frameTime - lastFrameTime).coerceAtLeast(1L)) / 1000f
                lastFrameTime = frameTime

                val now = System.currentTimeMillis()
                val elapsed = now - state.operationStartTime

                // ── Visibility Management ──
                if (!state.isVisible) {
                    if (elapsed >= ProgressConstants.UI_DEBOUNCE_DELAY_MS) {
                        state.isVisible = true
                    }
                }

                // ── Interpolation ──
                val target = state.targetProgress
                val delta = target - state.displayedProgress

                if (delta > ProgressConstants.CONVERGENCE_EPSILON) {
                    // Exponential smoothing step
                    var step = delta * ProgressConstants.SMOOTHING_FACTOR * dtSec

                    // Velocity clamp — prevent jarring jumps
                    val maxStep = ProgressConstants.MAX_VELOCITY_PER_SEC * dtSec
                    if (step > maxStep) {
                        step = maxStep
                    }

                    // Minimum velocity — prevent visual stalling
                    val minStep = ProgressConstants.MIN_VELOCITY_PER_SEC * dtSec
                    if (step < minStep) {
                        step = minStep
                    }

                    state.displayedProgress = (state.displayedProgress + step).coerceAtMost(target)
                }
            }

            // If the operation completed while we were mid-loop, markComplete()
            // already snapped displayedProgress to 1.0. The next LaunchedEffect
            // restart (keyed on isComplete) will handle the hold+dismiss.
            if (state.isComplete) break
        }
    }

    return state
}
