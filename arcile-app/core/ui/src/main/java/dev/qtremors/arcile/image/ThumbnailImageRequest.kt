package dev.qtremors.arcile.image

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest

fun buildThumbnailImageRequest(
    context: Context,
    data: Any?,
    cacheKey: String,
    sizePx: Int
): ImageRequest =
    ImageRequest.Builder(context)
        .data(data)
        .size(sizePx)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .crossfade(false)
        .build()

