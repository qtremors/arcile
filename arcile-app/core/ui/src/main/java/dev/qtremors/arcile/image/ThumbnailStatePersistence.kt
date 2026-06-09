package dev.qtremors.arcile.image

interface ThumbnailStatePersistence {
    fun recordLoaded(key: ThumbnailKey, thumbnailSizePx: Int)
    fun recordFailure(key: ThumbnailKey, thumbnailSizePx: Int)
    fun clearFailure(key: ThumbnailKey)
    fun clear()
}

object GlobalThumbnailStatePersistence {
    @Volatile
    var delegate: ThumbnailStatePersistence? = null

    fun restore(
        loadedVariantKeys: Collection<String>,
        failedIdentityKeys: Collection<String>
    ) {
        GlobalThumbnailLoadStateStore.restore(
            loadedVariantKeys = loadedVariantKeys,
            failedIdentityKeys = failedIdentityKeys
        )
        GlobalThumbnailFailureCache.restore(failedIdentityKeys)
    }
}
