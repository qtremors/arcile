package dev.qtremors.arcile.core.ui.image

import dev.qtremors.arcile.core.storage.domain.FileViewMode

data class ThumbnailPolicyInput(
    val userEnabled: Boolean,
    val viewMode: FileViewMode,
    val thumbnailSizePx: Int,
    val itemIndex: Int,
    val visibleRange: IntRange?,
    val isOperationActive: Boolean,
    val key: ThumbnailKey
)

class ThumbnailPolicy(
    private val failureCache: ThumbnailFailureCache = GlobalThumbnailFailureCache,
    private val loadStateStore: ThumbnailLoadStateStore = GlobalThumbnailLoadStateStore,
    private val bufferItems: Int = DEFAULT_BUFFER_ITEMS
) {
    fun shouldLoad(input: ThumbnailPolicyInput): Boolean {
        if (!input.userEnabled || input.isOperationActive) return false
        if (input.key.type == ThumbnailType.Unsupported) return false
        val variantKey = input.key.variantKey(input.thumbnailSizePx)
        if (loadStateStore.hasFailure(input.key.identityKey) || failureCache.hasFailure(input.key)) return false
        if (loadStateStore.isLoaded(variantKey)) {
            return isSafeForType(input.key, input.thumbnailSizePx)
        }
        if (!isInVisibleBudget(input.itemIndex, input.visibleRange, input.viewMode)) return false
        if (loadStateStore.isInFlight(variantKey)) {
            return isSafeForType(input.key, input.thumbnailSizePx)
        }
        return isSafeForType(input.key, input.thumbnailSizePx)
    }

    fun recordInFlight(key: ThumbnailKey, thumbnailSizePx: Int) {
        loadStateStore.markInFlight(key.variantKey(thumbnailSizePx))
    }

    fun recordLoaded(key: ThumbnailKey, thumbnailSizePx: Int) {
        loadStateStore.recordLoaded(key.variantKey(thumbnailSizePx))
        GlobalThumbnailStatePersistence.delegate?.recordLoaded(key, thumbnailSizePx)
    }

    fun recordFailure(key: ThumbnailKey, thumbnailSizePx: Int) {
        failureCache.recordFailure(key)
        loadStateStore.recordFailure(key.variantKey(thumbnailSizePx))
        GlobalThumbnailStatePersistence.delegate?.recordFailure(key, thumbnailSizePx)
    }

    fun clearFailure(key: ThumbnailKey) {
        failureCache.clearFailure(key)
        GlobalThumbnailStatePersistence.delegate?.clearFailure(key)
    }

    private fun isInVisibleBudget(index: Int, visibleRange: IntRange?, viewMode: FileViewMode): Boolean {
        if (visibleRange == null || visibleRange.isEmpty()) {
            return index < fallbackInitialBudget(viewMode)
        }
        val start = (visibleRange.first - bufferItems).coerceAtLeast(0)
        val end = visibleRange.last + bufferItems
        return index in start..end
    }

    private fun fallbackInitialBudget(viewMode: FileViewMode): Int =
        when (viewMode) {
            FileViewMode.LIST -> 18
            FileViewMode.GRID -> 30
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
        const val DEFAULT_BUFFER_ITEMS = 24
        const val MAX_EXPENSIVE_THUMBNAIL_PX = 512
        const val MAX_IMAGE_BYTES = 75L * 1024L * 1024L
        const val MAX_AUDIO_BYTES = 50L * 1024L * 1024L
        const val MAX_PDF_BYTES = 40L * 1024L * 1024L
        const val MAX_APK_BYTES = 250L * 1024L * 1024L
    }
}
