package dev.qtremors.arcile.core.ui.image

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.SensitiveThumbnailRequest

fun buildThumbnailImageRequest(
    context: Context,
    data: Any?,
    cacheKey: String,
    sizePx: Int
): ImageRequest {
    val sensitive = data is SensitiveThumbnailRequest
    return ImageRequest.Builder(context)
        .data(data)
        .size(sizePx)
        .memoryCacheKey((data as? SensitiveThumbnailRequest)?.memoryCacheKey ?: cacheKey)
        .diskCacheKey(if (sensitive) null else cacheKey)
        .diskCachePolicy(if (sensitive) CachePolicy.DISABLED else CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .crossfade(false)
        .build()
}

