package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.storage.data.db.CategorySummaryDao
import dev.qtremors.arcile.core.storage.data.db.CategorySummaryEntity
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.runtime.logging.AppLogger

internal class MediaStoreCategoryCache(
    private val dao: CategorySummaryDao,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    suspend fun save(scope: StorageScope, data: List<CategoryStorage>) {
        try {
            val cacheKey = key(scope) ?: return
            val cachedAt = nowMillis()
            dao.upsert(
                data.map { item ->
                    CategorySummaryEntity(
                        scopeKey = cacheKey,
                        categoryName = item.name,
                        sizeBytes = item.sizeBytes,
                        itemCount = 0,
                        cachedAt = cachedAt
                    )
                }
            )
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            AppLogger.e("MediaStoreClient", "Failed to save category cache")
        }
    }

    suspend fun get(scope: StorageScope): List<CategoryStorage>? {
        try {
            val cacheKey = key(scope) ?: return null
            val entities = dao.get(cacheKey)
            if (entities.isEmpty()) return null
            if (entities.any { nowMillis() - it.cachedAt > CACHE_TTL_MS }) return null

            val byName = entities.associateBy { it.categoryName }
            return FileCategories.all.map { category ->
                val entity = byName[category.name] ?: return null
                CategoryStorage(
                    name = category.name,
                    sizeBytes = entity.sizeBytes,
                    extensions = category.extensions
                )
            }
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            AppLogger.e("MediaStoreClient", "Category cache read failed")
            return null
        }
    }

    suspend fun clear() {
        dao.clear()
    }

    suspend fun invalidateVolumes(volumeIds: Set<String>) {
        dao.delete(listOf(GLOBAL_KEY) + volumeIds.map(::volumeKey))
    }

    private fun key(scope: StorageScope): String? =
        when (scope) {
            StorageScope.AllStorage -> GLOBAL_KEY
            is StorageScope.Volume -> volumeKey(scope.volumeId)
            else -> null
        }

    private fun volumeKey(volumeId: String): String =
        "volume_${volumeId.replace(NON_ALPHANUMERIC, "_")}"

    private companion object {
        const val GLOBAL_KEY = "global"
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        val NON_ALPHANUMERIC = Regex("[^a-zA-Z0-9]")
    }
}
