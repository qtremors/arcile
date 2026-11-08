package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.ui.ArcileGestureAxis

internal typealias AudioNowPlayingGesture = dev.qtremors.arcile.core.ui.audio.AudioNowPlayingGesture
internal fun resolveAudioNowPlayingGesture(
    lockedAxis: ArcileGestureAxis?, deltaX: Float, deltaY: Float, velocityX: Float, velocityY: Float,
    horizontalThreshold: Float, collapseThreshold: Float, queueThreshold: Float, minimumVelocity: Float
) = dev.qtremors.arcile.core.ui.audio.resolveAudioNowPlayingGesture(
    lockedAxis, deltaX, deltaY, velocityX, velocityY, horizontalThreshold, collapseThreshold, queueThreshold, minimumVelocity
)
