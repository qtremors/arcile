package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot

enum class ArcileGestureAxis {
    Horizontal,
    Vertical
}

enum class ArcileSwipeDirection {
    Left,
    Right,
    Up,
    Down
}

/** Shared thresholds for custom gestures that coexist with clickable or scrollable children. */
object ArcileGestureDefaults {
    const val DirectionLockRatio = 1.25f
    val MinimumSwipeDistance = 72.dp
    val MinimumSwipeVelocity = 800.dp
    val SystemEdgeExclusion = 24.dp
}

/**
 * Resolves an axis only after movement crosses touch slop and clearly favors one direction.
 * Ambiguous diagonal movement stays unowned so a parent or nested child can resolve it.
 */
fun arcileGestureAxis(
    deltaX: Float,
    deltaY: Float,
    touchSlop: Float,
    directionLockRatio: Float = ArcileGestureDefaults.DirectionLockRatio
): ArcileGestureAxis? {
    if (
        !deltaX.isFinite() ||
        !deltaY.isFinite() ||
        !touchSlop.isFinite() ||
        !directionLockRatio.isFinite() ||
        touchSlop < 0f ||
        directionLockRatio < 1f ||
        hypot(deltaX, deltaY) <= touchSlop
    ) {
        return null
    }

    val horizontal = abs(deltaX)
    val vertical = abs(deltaY)
    return when {
        horizontal >= vertical * directionLockRatio -> ArcileGestureAxis.Horizontal
        vertical >= horizontal * directionLockRatio -> ArcileGestureAxis.Vertical
        else -> null
    }
}

/** Resolves a terminal swipe by distance first, then by a same-axis fling. */
fun arcileSwipeDirection(
    axis: ArcileGestureAxis,
    deltaX: Float,
    deltaY: Float,
    velocityX: Float,
    velocityY: Float,
    minimumDistance: Float,
    minimumVelocity: Float
): ArcileSwipeDirection? {
    if (
        !deltaX.isFinite() ||
        !deltaY.isFinite() ||
        !velocityX.isFinite() ||
        !velocityY.isFinite() ||
        !minimumDistance.isFinite() ||
        minimumVelocity.isNaN() ||
        minimumDistance <= 0f ||
        minimumVelocity <= 0f
    ) {
        return null
    }

    val delta = if (axis == ArcileGestureAxis.Horizontal) deltaX else deltaY
    val velocity = if (axis == ArcileGestureAxis.Horizontal) velocityX else velocityY
    val signedMotion = when {
        abs(delta) >= minimumDistance -> delta
        abs(velocity) >= minimumVelocity -> velocity
        else -> return null
    }
    return when (axis) {
        ArcileGestureAxis.Horizontal -> if (signedMotion < 0f) {
            ArcileSwipeDirection.Left
        } else {
            ArcileSwipeDirection.Right
        }
        ArcileGestureAxis.Vertical -> if (signedMotion < 0f) {
            ArcileSwipeDirection.Up
        } else {
            ArcileSwipeDirection.Down
        }
    }
}

/** True only while a single-pointer gesture remains within tap slop and platform tap time. */
fun arcileTapEligible(
    deltaX: Float,
    deltaY: Float,
    durationMillis: Long,
    touchSlop: Float,
    tapTimeoutMillis: Long,
    hadMultiplePointers: Boolean,
    movementConsumedByAnotherOwner: Boolean
): Boolean =
    deltaX.isFinite() &&
        deltaY.isFinite() &&
        touchSlop.isFinite() &&
        durationMillis in 0 until tapTimeoutMillis &&
        touchSlop >= 0f &&
        tapTimeoutMillis > 0L &&
        !hadMultiplePointers &&
        !movementConsumedByAnotherOwner &&
        hypot(deltaX, deltaY) <= touchSlop

/**
 * Adds a terminal horizontal swipe without stealing taps, vertical scrolling, nested drags,
 * long presses, multi-touch gestures, or Android edge navigation.
 */
fun Modifier.arcileHorizontalSwipe(
    enabled: Boolean = true,
    minimumDistance: Dp = ArcileGestureDefaults.MinimumSwipeDistance,
    minimumVelocity: Dp = ArcileGestureDefaults.MinimumSwipeVelocity,
    edgeExclusion: Dp = ArcileGestureDefaults.SystemEdgeExclusion,
    onSwipe: (ArcileSwipeDirection) -> Unit
): Modifier = composed {
    val currentOnSwipe = rememberUpdatedState(onSwipe)
    pointerInput(enabled, minimumDistance, minimumVelocity, edgeExclusion) {
        if (!enabled) return@pointerInput
        val minimumDistancePx = minimumDistance.toPx()
        val minimumVelocityPx = minimumVelocity.toPx()
        val edgeExclusionPx = edgeExclusion.toPx().coerceAtLeast(0f)
        if (minimumDistancePx <= 0f || minimumVelocityPx <= 0f) return@pointerInput

        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            val startsInSystemEdge = down.position.x <= edgeExclusionPx ||
                down.position.x >= size.width - edgeExclusionPx
            var latestPosition = down.position
            var ownsGesture = false
            var cancelled = startsInSystemEdge
            var released = false
            val velocityTracker = VelocityTracker().apply {
                addPosition(down.uptimeMillis, down.position)
            }

            while (!released) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.any { change ->
                        change.id != down.id && (change.pressed || change.previousPressed)
                    }
                ) {
                    cancelled = true
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                latestPosition = change.position
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val movement = change.position - change.previousPosition
                val total = change.position - down.position

                if (!cancelled && !ownsGesture) {
                    if (change.isConsumed && movement != Offset.Zero) {
                        cancelled = true
                    } else if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                        cancelled = true
                    } else {
                        when (
                            arcileGestureAxis(
                                deltaX = total.x,
                                deltaY = total.y,
                                touchSlop = viewConfiguration.touchSlop
                            )
                        ) {
                            ArcileGestureAxis.Horizontal -> {
                                ownsGesture = true
                                change.consume()
                            }
                            ArcileGestureAxis.Vertical -> cancelled = true
                            null -> Unit
                        }
                    }
                } else if (ownsGesture) {
                    change.consume()
                }
                released = !change.pressed
            }

            if (released && ownsGesture && !cancelled) {
                val total = latestPosition - down.position
                val velocity = velocityTracker.calculateVelocity()
                arcileSwipeDirection(
                    axis = ArcileGestureAxis.Horizontal,
                    deltaX = total.x,
                    deltaY = total.y,
                    velocityX = velocity.x,
                    velocityY = velocity.y,
                    minimumDistance = minimumDistancePx,
                    minimumVelocity = minimumVelocityPx
                )?.let(currentOnSwipe.value)
            }
        }
    }
}
