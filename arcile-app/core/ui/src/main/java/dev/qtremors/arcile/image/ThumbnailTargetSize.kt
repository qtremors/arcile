package dev.qtremors.arcile.image

import coil.request.Options
import coil.size.Dimension

object ThumbnailTargetSize {
    const val MIN_PX = 96
    const val DEFAULT_PX = 256
    const val MAX_PX = 1024
    const val MAX_EXPENSIVE_PX = ThumbnailPolicy.MAX_EXPENSIVE_THUMBNAIL_PX

    fun fromBounds(widthPx: Int, heightPx: Int = widthPx, maxPx: Int = MAX_PX): Int {
        val largest = maxOf(widthPx, heightPx).takeIf { it > 0 } ?: DEFAULT_PX
        return largest.coerceIn(MIN_PX, maxPx)
    }

    fun fromOptions(options: Options, fallbackPx: Int = DEFAULT_PX, maxPx: Int = MAX_PX): Int {
        val width = (options.size.width as? Dimension.Pixels)?.px ?: fallbackPx
        val height = (options.size.height as? Dimension.Pixels)?.px ?: fallbackPx
        return fromBounds(width, height, maxPx)
    }
}
