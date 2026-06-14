package dev.qtremors.arcile.image

import java.util.concurrent.ConcurrentHashMap

open class ThumbnailLoadStateStore {
    private val loadedVariants = ConcurrentHashMap.newKeySet<String>()
    private val failedIdentities = ConcurrentHashMap.newKeySet<String>()
    private val inFlightVariants = ConcurrentHashMap.newKeySet<String>()

    fun isLoaded(key: ThumbnailVariantKey): Boolean = loadedVariants.contains(key.cacheKey)

    fun hasFailure(key: ThumbnailIdentityKey): Boolean = failedIdentities.contains(key.cacheKey)

    fun isInFlight(key: ThumbnailVariantKey): Boolean = inFlightVariants.contains(key.cacheKey)

    fun markInFlight(key: ThumbnailVariantKey) {
        inFlightVariants.add(key.cacheKey)
    }

    fun recordLoaded(key: ThumbnailVariantKey) {
        inFlightVariants.remove(key.cacheKey)
        failedIdentities.remove(key.identity.cacheKey)
        loadedVariants.add(key.cacheKey)
    }

    fun recordFailure(key: ThumbnailVariantKey) {
        inFlightVariants.remove(key.cacheKey)
        failedIdentities.add(key.identity.cacheKey)
    }

    fun restore(
        loadedVariantKeys: Collection<String>,
        failedIdentityKeys: Collection<String>
    ) {
        loadedVariants.addAll(loadedVariantKeys)
        failedIdentities.addAll(failedIdentityKeys)
    }

    fun clear() {
        loadedVariants.clear()
        failedIdentities.clear()
        inFlightVariants.clear()
    }

    fun stats(): ThumbnailLoadStateStats =
        ThumbnailLoadStateStats(
            loadedCount = loadedVariants.size,
            failedCount = failedIdentities.size,
            inFlightCount = inFlightVariants.size
        )
}

data class ThumbnailLoadStateStats(
    val loadedCount: Int,
    val failedCount: Int,
    val inFlightCount: Int
)

object GlobalThumbnailLoadStateStore : ThumbnailLoadStateStore()
