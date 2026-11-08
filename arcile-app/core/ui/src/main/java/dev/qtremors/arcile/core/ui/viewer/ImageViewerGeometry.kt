package dev.qtremors.arcile.core.ui.viewer

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

@OptIn(ExperimentalMaterial3Api::class)

internal const val VIEWER_MIN_STABLE_SCALE = 0.000001f
internal const val VIEWER_MAX_STABLE_SCALE = 1_000_000f

fun viewerReleaseScale(scale: Float): Float =
    scale.coerceIn(VIEWER_MIN_STABLE_SCALE, VIEWER_MAX_STABLE_SCALE)

fun viewerRenderScale(scale: Float, dragFraction: Float): Float =
    (scale * (1f - dragFraction * 0.15f)).coerceIn(VIEWER_MIN_STABLE_SCALE, VIEWER_MAX_STABLE_SCALE)

fun viewerPanLimit(scale: Float, viewportSize: Int): Float =
    ((scale - 1f).coerceAtLeast(0f) * viewportSize / 2f)

fun viewerFittedContentSize(
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    rotationDegrees: Float
): Size {
    if (
        viewportWidth <= 0f ||
        viewportHeight <= 0f ||
        imageWidth <= 0f ||
        imageHeight <= 0f
    ) {
        return Size(viewportWidth.coerceAtLeast(0f), viewportHeight.coerceAtLeast(0f))
    }
    val quarterTurns = ((rotationDegrees / 90f).toInt() % 4 + 4) % 4
    val rotatedWidth = if (quarterTurns % 2 == 0) imageWidth else imageHeight
    val rotatedHeight = if (quarterTurns % 2 == 0) imageHeight else imageWidth
    val fitScale = minOf(viewportWidth / rotatedWidth, viewportHeight / rotatedHeight)
    return Size(rotatedWidth * fitScale, rotatedHeight * fitScale)
}

fun viewerPanLimit(
    scale: Float,
    contentSize: Float,
    viewportSize: Float
): Float = ((contentSize * scale - viewportSize) / 2f).coerceAtLeast(0f)

fun viewerOffsetForScale(
    currentOffset: Offset,
    oldScale: Float,
    newScale: Float,
    centroid: Offset,
    viewportCenter: Offset,
    pan: Offset = Offset.Zero
): Offset {
    if (oldScale <= 0f || !oldScale.isFinite() || !newScale.isFinite()) return currentOffset + pan
    val ratio = newScale / oldScale
    val focalFromCenter = centroid - viewportCenter
    return currentOffset + (focalFromCenter - currentOffset) * (1f - ratio) + pan
}
