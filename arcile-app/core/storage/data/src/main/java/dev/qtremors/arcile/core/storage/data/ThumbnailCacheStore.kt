package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.db.ThumbnailDao
import dev.qtremors.arcile.core.storage.data.db.ThumbnailEntryEntity
import dev.qtremors.arcile.core.storage.data.db.ThumbnailVariantEntity
import dev.qtremors.arcile.di.ArcileDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ThumbnailCacheSnapshot(
    val loadedVariantKeys: List<String>,
    val failedIdentityKeys: List<String>
)

data class ThumbnailCacheRecord(
    val identityKey: String,
    val variantKey: String,
    val source: String,
    val extension: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val contentUri: String?,
    val type: String,
    val sizeBucketPx: Int
)

interface ThumbnailCacheStore {
    suspend fun snapshot(): ThumbnailCacheSnapshot
    suspend fun recordLoaded(record: ThumbnailCacheRecord)
    suspend fun recordFailure(record: ThumbnailCacheRecord, message: String? = null)
    suspend fun clearFailure(identityKey: String)
    suspend fun invalidateSources(paths: Collection<String>)
    suspend fun clear()
}

object NoOpThumbnailCacheStore : ThumbnailCacheStore {
    override suspend fun snapshot(): ThumbnailCacheSnapshot = ThumbnailCacheSnapshot(emptyList(), emptyList())
    override suspend fun recordLoaded(record: ThumbnailCacheRecord) = Unit
    override suspend fun recordFailure(record: ThumbnailCacheRecord, message: String?) = Unit
    override suspend fun clearFailure(identityKey: String) = Unit
    override suspend fun invalidateSources(paths: Collection<String>) = Unit
    override suspend fun clear() = Unit
}

@Singleton
class DefaultThumbnailCacheStore @Inject constructor(
    private val thumbnailDao: ThumbnailDao,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : ThumbnailCacheStore {
    private companion object {
        const val MAX_VARIANTS = 20_000
    }

    override suspend fun snapshot(): ThumbnailCacheSnapshot = withContext(dispatchers.io) {
        ThumbnailCacheSnapshot(
            loadedVariantKeys = thumbnailDao.loadedVariantKeys(),
            failedIdentityKeys = thumbnailDao.failedIdentityKeys()
        )
    }

    override suspend fun recordLoaded(record: ThumbnailCacheRecord) = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        thumbnailDao.upsertEntry(
            record.toEntity(
                lastSuccessAt = now,
                lastFailureAt = null,
                failureCount = 0,
                failureMessage = null
            )
        )
        thumbnailDao.upsertVariant(
            ThumbnailVariantEntity(
                variantKey = record.variantKey,
                identityKey = record.identityKey,
                sizeBucketPx = record.sizeBucketPx,
                generatedAt = now,
                lastAccessedAt = now
            )
        )
        pruneIfNeeded()
    }

    override suspend fun recordFailure(record: ThumbnailCacheRecord, message: String?) = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        thumbnailDao.deleteVariantsForIdentity(record.identityKey)
        thumbnailDao.upsertEntry(
            record.toEntity(
                lastSuccessAt = null,
                lastFailureAt = now,
                failureCount = 1,
                failureMessage = message
            )
        )
    }

    override suspend fun clearFailure(identityKey: String) = withContext(dispatchers.io) {
        thumbnailDao.clearFailure(identityKey)
    }

    override suspend fun invalidateSources(paths: Collection<String>) = withContext(dispatchers.io) {
        val normalized = paths.map { it.replace('\\', '/') }.filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) return@withContext
        normalized.forEach { path ->
            thumbnailDao.deleteEntriesForSources(listOf(path), "$path/%")
        }
        thumbnailDao.deleteOrphanVariants()
    }

    override suspend fun clear() = withContext(dispatchers.io) {
        thumbnailDao.clearAll()
    }

    private suspend fun pruneIfNeeded() {
        val count = thumbnailDao.variantCount()
        if (count <= MAX_VARIANTS) return
        val overflow = count - MAX_VARIANTS
        val oldest = thumbnailDao.oldestVariantKeys(overflow)
        if (oldest.isNotEmpty()) {
            thumbnailDao.deleteVariants(oldest)
        }
    }

    private fun ThumbnailCacheRecord.toEntity(
        lastSuccessAt: Long?,
        lastFailureAt: Long?,
        failureCount: Int,
        failureMessage: String?
    ): ThumbnailEntryEntity =
        ThumbnailEntryEntity(
            identityKey = identityKey,
            source = source,
            extension = extension,
            sizeBytes = sizeBytes,
            lastModified = lastModified,
            contentUri = contentUri,
            type = type,
            lastSuccessAt = lastSuccessAt,
            lastFailureAt = lastFailureAt,
            failureCount = failureCount,
            failureMessage = failureMessage
        )
}
