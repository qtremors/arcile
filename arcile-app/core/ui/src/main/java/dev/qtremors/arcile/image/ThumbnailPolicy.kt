package dev.qtremors.arcile.image

import dev.qtremors.arcile.core.storage.domain.BrowserViewMode

data class ThumbnailPolicyInput(
    val userEnabled: Boolean,
    val viewMode: BrowserViewMode,
    val thumbnailSizePx: Int,
    val itemIndex: Int,
    val visibleRange: IntRange?,
    val isOperationActive: Boolean,
    val key: ThumbnailKey
)

class ThumbnailPolicy(
    private val failureCache: ThumbnailFailureCache = GlobalThumbnailFailureCache,
    private val bufferItems: Int = DEFAULT_BUFFER_ITEMS
) {
    fun shouldLoad(input: ThumbnailPolicyInput): Boolean {
        if (!input.userEnabled || input.isOperationActive) return false
        if (failureCache.hasFailure(input.key)) return false
        if (input.key.type == ThumbnailType.Unsupported) return false
        if (!isInVisibleBudget(input.itemIndex, input.visibleRange, input.viewMode)) return false
        return isSafeForType(input.key, input.thumbnailSizePx)
    }

    fun recordFailure(key: ThumbnailKey) {
        failureCache.recordFailure(key)
    }

    fun clearFailure(key: ThumbnailKey) {
        failureCache.clearFailure(key)
    }

    private fun isInVisibleBudget(index: Int, visibleRange: IntRange?, viewMode: BrowserViewMode): Boolean {
        if (visibleRange == null || visibleRange.isEmpty()) {
            return index < fallbackInitialBudget(viewMode)
        }
        val start = (visibleRange.first - bufferItems).coerceAtLeast(0)
        val end = visibleRange.last + bufferItems
        return index in start..end
    }

    private fun fallbackInitialBudget(viewMode: BrowserViewMode): Int =
        when (viewMode) {
            BrowserViewMode.LIST -> 18
            BrowserViewMode.GRID -> 30
        }

    private fun isSafeForType(key: ThumbnailKey, thumbnailSizePx: Int): Boolean =
        when (key.type) {
            ThumbnailType.Image -> key.sizeBytes <= MAX_IMAGE_BYTES
            ThumbnailType.Video -> true
            ThumbnailType.Audio -> key.sizeBytes <= MAX_AUDIO_BYTES
            ThumbnailType.Pdf -> key.sizeBytes <= MAX_PDF_BYTES && thumbnailSizePx <= MAX_EXPENSIVE_THUMBNAIL_PX
            ThumbnailType.Apk -> key.sizeBytes <= MAX_APK_BYTES && thumbnailSizePx <= MAX_EXPENSIVE_THUMBNAIL_PX
            ThumbnailType.Unsupported -> false
        }

    companion object {
        const val DEFAULT_BUFFER_ITEMS = 8
        const val MAX_EXPENSIVE_THUMBNAIL_PX = 512
        const val MAX_IMAGE_BYTES = 75L * 1024L * 1024L
        const val MAX_AUDIO_BYTES = 50L * 1024L * 1024L
        const val MAX_PDF_BYTES = 40L * 1024L * 1024L
        const val MAX_APK_BYTES = 250L * 1024L * 1024L
    }
}
