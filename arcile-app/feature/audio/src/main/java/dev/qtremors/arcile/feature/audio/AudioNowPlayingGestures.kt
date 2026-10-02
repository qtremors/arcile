package dev.qtremors.arcile.feature.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.qtremors.arcile.core.ui.ArcileGestureAxis
import dev.qtremors.arcile.core.ui.ArcileSwipeDirection
import dev.qtremors.arcile.core.ui.arcileSwipeDirection

internal enum class AudioNowPlayingGesture {
    Previous,
    Next,
    Collapse,
    Queue,
    None
}

internal fun resolveAudioNowPlayingGesture(
    lockedAxis: ArcileGestureAxis?,
    deltaX: Float,
    deltaY: Float,
    velocityX: Float,
    velocityY: Float,
    horizontalThreshold: Float,
    collapseThreshold: Float,
    queueThreshold: Float,
    minimumVelocity: Float
): AudioNowPlayingGesture {
    val axis = lockedAxis ?: return AudioNowPlayingGesture.None
    val minimumDistance = when (axis) {
        ArcileGestureAxis.Horizontal -> horizontalThreshold
        ArcileGestureAxis.Vertical -> if (deltaY >= 0f) collapseThreshold else queueThreshold
    }
    return when (
        arcileSwipeDirection(
            axis = axis,
            deltaX = deltaX,
            deltaY = deltaY,
            velocityX = velocityX,
            velocityY = velocityY,
            minimumDistance = minimumDistance,
            minimumVelocity = minimumVelocity
        )
    ) {
        ArcileSwipeDirection.Left -> AudioNowPlayingGesture.Next
        ArcileSwipeDirection.Right -> AudioNowPlayingGesture.Previous
        ArcileSwipeDirection.Down -> AudioNowPlayingGesture.Collapse
        ArcileSwipeDirection.Up -> AudioNowPlayingGesture.Queue
        null -> AudioNowPlayingGesture.None
    }
}
