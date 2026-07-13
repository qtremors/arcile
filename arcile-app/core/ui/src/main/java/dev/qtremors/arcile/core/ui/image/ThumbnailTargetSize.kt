package dev.qtremors.arcile.core.ui.image

import coil.request.Options
import coil.size.Dimension

object ThumbnailTargetSize {
    const val MIN_PX = 96
    const val DEFAULT_PX = 256
    const val MAX_PX = 1024
    const val MAX_EXPENSIVE_PX = ThumbnailPolicy.MAX_EXPENSIVE_THUMBNAIL_PX
    private val BUCKETS = intArrayOf(128, 256, 384, 512, 768, 1024)

    fun fromBounds(widthPx: Int, heightPx: Int = widthPx, maxPx: Int = MAX_PX): Int {
        val largest = maxOf(widthPx, heightPx).takeIf { it > 0 } ?: DEFAULT_PX
        return largest.coerceIn(MIN_PX, maxPx)
    }

    fun bucket(sizePx: Int): Int {
        val normalized = fromBounds(sizePx)
        return BUCKETS.firstOrNull { normalized <= it } ?: BUCKETS.last()
    }

    fun fromOptions(options: Options, fallbackPx: Int = DEFAULT_PX, maxPx: Int = MAX_PX): Int {
        val width = (options.size.width as? Dimension.Pixels)?.px ?: fallbackPx
        val height = (options.size.height as? Dimension.Pixels)?.px ?: fallbackPx
        return fromBounds(width, height, maxPx)
    }
}
