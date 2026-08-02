package dev.qtremors.arcile.core.ui.image

import coil.key.Keyer
import coil.request.Options

class ThumbnailKeyer : Keyer<ThumbnailKey> {
    override fun key(data: ThumbnailKey, options: Options): String {
        val maxSizePx = when (data.type) {
            ThumbnailType.Pdf,
            ThumbnailType.Apk -> ThumbnailTargetSize.MAX_EXPENSIVE_PX
            else -> ThumbnailTargetSize.MAX_PX
        }
        val targetSizePx = ThumbnailTargetSize.fromOptions(
            options = options,
            maxPx = maxSizePx
        )
        return data.variantKey(targetSizePx).cacheKey
    }
}
