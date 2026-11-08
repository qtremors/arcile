package dev.qtremors.arcile.feature.videoplayer

import java.util.Locale

internal enum class ViewerThumbnailScrollAction { Jump, Animate, None }

internal fun viewerThumbnailScrollAction(
    previousPage: Int?,
    currentPage: Int,
    maxAnimatedDistance: Int = 12
): ViewerThumbnailScrollAction {
    if (currentPage < 0) return ViewerThumbnailScrollAction.None
    val previous = previousPage ?: return ViewerThumbnailScrollAction.Jump
    if (previous == currentPage) return ViewerThumbnailScrollAction.None
    return if (kotlin.math.abs(currentPage - previous) <= maxAnimatedDistance) {
        ViewerThumbnailScrollAction.Animate
    } else {
        ViewerThumbnailScrollAction.Jump
    }
}

internal fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
