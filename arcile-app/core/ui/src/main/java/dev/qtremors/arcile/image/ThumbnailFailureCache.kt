package dev.qtremors.arcile.image

open class ThumbnailFailureCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val failedKeys = object : LinkedHashMap<String, Unit>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun recordFailure(key: ThumbnailKey) {
        failedKeys[key.cacheKey] = Unit
    }

    @Synchronized
    fun clearFailure(key: ThumbnailKey) {
        failedKeys.remove(key.cacheKey)
    }

    @Synchronized
    fun restore(cacheKeys: Collection<String>) {
        cacheKeys.forEach { failedKeys[it] = Unit }
    }

    @Synchronized
    fun hasFailure(key: ThumbnailKey): Boolean = failedKeys.containsKey(key.cacheKey)

    @Synchronized
    fun clear() {
        failedKeys.clear()
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 256
    }
}

object GlobalThumbnailFailureCache : ThumbnailFailureCache()
