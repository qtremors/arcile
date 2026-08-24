package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

/** Keeps ordinary thumbnail-strip scrolling intact and scrubs by absolute position after a hold. */
fun Modifier.viewerThumbnailFastScroll(
    state: LazyListState,
    orientation: Orientation,
    onFastScrollStart: () -> Unit = {}
): Modifier = pointerInput(state, orientation) {
    detectDragGesturesAfterLongPress(
        onDragStart = { position ->
            onFastScrollStart()
            state.requestViewerThumbnailPosition(position, orientation)
        },
        onDrag = { change, _ ->
            change.consume()
            state.requestViewerThumbnailPosition(change.position, orientation)
        }
    )
}

private fun LazyListState.requestViewerThumbnailPosition(
    position: Offset,
    orientation: Orientation
) {
    val layout = layoutInfo
    val target = viewerThumbnailFastScrollTarget(
        pointerPosition = viewerThumbnailFastScrollAxisPosition(position, orientation),
        viewportStart = layout.viewportStartOffset.toFloat(),
        viewportEnd = layout.viewportEndOffset.toFloat(),
        totalItems = layout.totalItemsCount
    ) ?: return
    requestScrollToItem(target)
}

internal fun viewerThumbnailFastScrollTarget(
    pointerPosition: Float,
    viewportStart: Float,
    viewportEnd: Float,
    totalItems: Int
): Int? {
    if (totalItems <= 0 || viewportEnd <= viewportStart) return null
    if (totalItems == 1) return 0
    val progress = ((pointerPosition - viewportStart) / (viewportEnd - viewportStart))
        .coerceIn(0f, 1f)
    return (progress * (totalItems - 1)).roundToInt()
}

internal fun viewerThumbnailFastScrollAxisPosition(
    position: Offset,
    orientation: Orientation,
): Float = if (orientation == Orientation.Horizontal) position.x else position.y
