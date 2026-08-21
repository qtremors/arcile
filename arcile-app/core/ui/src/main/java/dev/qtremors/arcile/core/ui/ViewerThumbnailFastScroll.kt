package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

private const val ViewerThumbnailFastScrollMultiplier = 5f

/** Keeps ordinary thumbnail-strip scrolling intact and accelerates a drag after a hold. */
fun Modifier.viewerThumbnailFastScroll(
    state: LazyListState,
    orientation: Orientation,
    onFastScrollStart: () -> Unit = {}
): Modifier = pointerInput(state, orientation) {
    detectDragGesturesAfterLongPress(
        onDragStart = { onFastScrollStart() },
        onDrag = { change, dragAmount ->
            change.consume()
            state.dispatchRawDelta(
                viewerThumbnailFastScrollDelta(dragAmount, orientation)
            )
        }
    )
}

internal fun viewerThumbnailFastScrollDelta(
    dragAmount: Offset,
    orientation: Orientation,
    multiplier: Float = ViewerThumbnailFastScrollMultiplier
): Float {
    val axisDelta = if (orientation == Orientation.Horizontal) dragAmount.x else dragAmount.y
    return -axisDelta * multiplier
}
