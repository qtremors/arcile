package dev.qtremors.arcile.feature.audio

internal fun audioProgressFraction(positionMs: Float, durationMs: Long): Float =
    if (durationMs <= 0L) {
        0f
    } else {
        (positionMs / durationMs.toFloat()).coerceIn(0f, 1f)
    }
