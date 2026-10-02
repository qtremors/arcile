package dev.qtremors.arcile.feature.videoplayer

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

internal data class VideoViewerState(
    val isInitialized: Boolean = false,
    val files: PersistentList<FileModel> = persistentListOf(),
    val displayedFiles: PersistentList<FileModel> = persistentListOf(),
    val favoriteFiles: PersistentSet<String> = persistentSetOf(),
    val selectedFiles: PersistentSet<String> = persistentSetOf(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val isShredChecked: Boolean = false,
    val viewerSessionInitialPath: String? = null,
    val viewerCurrentPath: String? = null,
    val viewerMetadataPath: String? = null,
    val viewerUiVisible: Boolean = true,
    val showThumbnails: Boolean = true
) {
    fun withDeleteDialogsHidden(selected: PersistentSet<String> = selectedFiles): VideoViewerState =
        copy(
            showTrashConfirmation = false,
            showPermanentDeleteConfirmation = false,
            showMixedDeleteExplanation = false,
            selectedFiles = selected
        )
}

internal fun <K, V> MutableMap<K, V>.putBounded(key: K, value: V, maxEntries: Int) {
    require(maxEntries > 0)
    if (key !in this && size >= maxEntries) keys.firstOrNull()?.let(::remove)
    this[key] = value
}

internal data class VideoSeekState(
    val progress: Float,
    val canSeek: Boolean
)

internal fun videoSeekState(position: Long, duration: Long): VideoSeekState {
    if (duration <= 0L) return VideoSeekState(progress = 0f, canSeek = false)
    return VideoSeekState(
        progress = (position.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f),
        canSeek = true
    )
}

internal fun videoPlaybackEndTimeText(
    position: Long,
    duration: Long,
    showRemaining: Boolean
): String {
    if (!showRemaining) return formatPlaybackTime(duration)
    val remaining = (duration - position).coerceAtLeast(0L)
    return "−${formatPlaybackTime(remaining)}"
}

internal fun videoVerticalGestureAllowed(
    startX: Float,
    startY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    leftGestureInset: Float,
    rightGestureInset: Float,
    topGestureInset: Float,
    bottomGestureInset: Float
): Boolean {
    if (viewportWidth <= 0f || viewportHeight <= 0f) return false
    val safeLeft = leftGestureInset.coerceAtLeast(0f)
    val safeRight = rightGestureInset.coerceAtLeast(0f)
    val safeTop = topGestureInset.coerceAtLeast(0f)
    val safeBottom = bottomGestureInset.coerceAtLeast(0f)
    return startX >= safeLeft &&
        startX < (viewportWidth - safeRight).coerceAtLeast(safeLeft) &&
        startY >= safeTop &&
        startY < (viewportHeight - safeBottom).coerceAtLeast(safeTop)
}

internal data class VideoZoomTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

internal fun videoZoomTransformAfterGesture(
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    zoomChange: Float,
    panX: Float,
    panY: Float,
    viewportWidth: Float,
    viewportHeight: Float
): VideoZoomTransform {
    val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
    if (nextScale <= 1f) return VideoZoomTransform(1f, 0f, 0f)
    val maxOffsetX = (viewportWidth.coerceAtLeast(0f) * (nextScale - 1f)) / 2f
    val maxOffsetY = (viewportHeight.coerceAtLeast(0f) * (nextScale - 1f)) / 2f
    return VideoZoomTransform(
        scale = nextScale,
        offsetX = (offsetX + panX).coerceIn(-maxOffsetX, maxOffsetX),
        offsetY = (offsetY + panY).coerceIn(-maxOffsetY, maxOffsetY)
    )
}

internal fun videoScrubTargetPosition(progress: Float, duration: Long): Long =
    (progress.coerceIn(0f, 1f) * duration.coerceAtLeast(0L)).toLong()

internal fun videoScrubProgressForPointer(pointerX: Float, trackWidth: Float): Float {
    if (trackWidth <= 0f) return 0f
    return (pointerX / trackWidth).coerceIn(0f, 1f)
}

internal data class VideoScrubPlaybackTransition(
    val isScrubbing: Boolean,
    val resumeWhenFinished: Boolean,
    val pausePlayback: Boolean,
    val resumePlayback: Boolean
)

internal fun videoScrubPlaybackTransition(
    wasScrubbing: Boolean,
    resumeWhenFinished: Boolean,
    scrubbing: Boolean,
    isPlaying: Boolean
): VideoScrubPlaybackTransition = when {
    scrubbing && !wasScrubbing -> VideoScrubPlaybackTransition(
        isScrubbing = true,
        resumeWhenFinished = isPlaying,
        pausePlayback = isPlaying,
        resumePlayback = false
    )

    !scrubbing && wasScrubbing -> VideoScrubPlaybackTransition(
        isScrubbing = false,
        resumeWhenFinished = false,
        pausePlayback = false,
        resumePlayback = resumeWhenFinished
    )

    else -> VideoScrubPlaybackTransition(
        isScrubbing = scrubbing,
        resumeWhenFinished = resumeWhenFinished && scrubbing,
        pausePlayback = false,
        resumePlayback = false
    )
}

internal fun shouldShowVideoLoadingIndicator(
    isBuffering: Boolean,
    showPlaceholder: Boolean,
    placeholderDelayElapsed: Boolean
): Boolean = isBuffering && (!showPlaceholder || placeholderDelayElapsed)

internal fun videoPlayerSurfaceCanAttach(
    isPageFocused: Boolean,
    loadedPath: String?,
    pagePath: String
): Boolean = isPageFocused && loadedPath == pagePath

internal fun videoRenderedPathForFirstFrame(
    currentMediaId: String?,
    loadedPath: String?,
    transitionedPath: String?
): String? = currentMediaId?.takeIf {
    it == loadedPath && it == transitionedPath
}

internal fun videoBackgroundPlaybackAllowed(securityScopeId: String?): Boolean =
    securityScopeId == null

internal fun shouldKeepVideoPlayerActive(
    allowBackgroundPlayback: Boolean,
    lifecycleStarted: Boolean
): Boolean = allowBackgroundPlayback || lifecycleStarted
