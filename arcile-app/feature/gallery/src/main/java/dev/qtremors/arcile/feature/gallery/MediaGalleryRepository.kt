package dev.qtremors.arcile.feature.gallery

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.ImageCatalogRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.storage.domain.storagePathName
import dev.qtremors.arcile.core.storage.domain.isStorageDescendantOrSelf
import dev.qtremors.arcile.core.storage.domain.normalizeStoragePath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal interface MediaGalleryRepository {
    val mutationEvents: Flow<dev.qtremors.arcile.core.storage.domain.StorageMutationEvent>
    suspend fun loadMedia(
        volumeId: String?,
        forceRefresh: Boolean = false,
        categoryId: String = FileCategories.Images.id.value
    ): MediaGallerySnapshot
    fun invalidate(paths: Collection<String> = emptyList())
}

internal data class MediaGallerySnapshot(
    val files: List<FileModel>,
    val folders: List<MediaGalleryFolder>,
    val aspectRatios: Map<String, Float>,
    val isStale: Boolean
)

internal data class MediaGalleryFolder(
    val path: String?,
    val label: String,
    val count: Int,
    val lastModified: Long
)

internal class DefaultMediaGalleryRepository @Inject constructor(
    private val imageCatalogRepository: ImageCatalogRepository,
    private val searchRepository: SearchRepository,
    private val storageMutationNotifier: StorageMutationNotifier,
    private val dispatchers: ArcileDispatchers
) : MediaGalleryRepository {
    private val snapshotLock = Any()
    private val snapshots = LinkedHashMap<String, MediaGallerySnapshot>(8, 0.75f, true)

    override val mutationEvents = storageMutationNotifier.events

    override suspend fun loadMedia(
        volumeId: String?,
        forceRefresh: Boolean,
        categoryId: String
    ): MediaGallerySnapshot {
        val key = "${categoryId.lowercase()}:${volumeId.orEmpty()}"
        val cached = synchronized(snapshotLock) { snapshots[key] }
        if (cached != null && !forceRefresh) return cached.copy(isStale = false)

        val category = FileCategories.find(categoryId) ?: FileCategories.Images
        val imageCatalog = if (category == FileCategories.Images) {
            imageCatalogRepository.loadImages(volumeId, forceRefresh).getOrThrow()
        } else {
            null
        }
        val categoryFiles = imageCatalog?.items?.map { it.file }
            ?: searchRepository.getFilesByCategory(
                StorageScope.Category(
                    volumeId?.takeIf(String::isNotBlank),
                    category.storageName
                ),
                category.storageName
            ).getOrThrow()
        val snapshot = withContext(dispatchers.default) {
            val files = categoryFiles
                .distinctBy { it.reference }
                .sortedByDescending { it.lastModified }
            val aspectRatios = imageCatalog?.items
                ?.mapNotNull { item -> item.aspectRatio?.let { item.file.reference to it } }
                ?.toMap()
                .orEmpty()
            MediaGallerySnapshot(
                files = files,
                folders = buildMediaGalleryFolders(files),
                aspectRatios = aspectRatios,
                isStale = imageCatalog?.isStale == true
            )
        }
        synchronized(snapshotLock) {
            snapshots[key] = snapshot
            while (snapshots.size > MAX_SNAPSHOT_CACHE_ENTRIES) {
                snapshots.entries.iterator().run { next(); remove() }
            }
        }
        return snapshot
    }

    override fun invalidate(paths: Collection<String>) {
        synchronized(snapshotLock) {
            if (paths.isEmpty()) {
                snapshots.clear()
                imageCatalogRepository.invalidate()
                return
            }
            imageCatalogRepository.invalidate(paths)
            val affected = paths.map(::normalizeStoragePath)
            snapshots.entries.removeIf { entry ->
                entry.value.files.any { file ->
                    val normalized = normalizeStoragePath(file.reference)
                    affected.any { changed ->
                        isStorageDescendantOrSelf(normalized, changed) ||
                            isStorageDescendantOrSelf(changed, normalized)
                    }
                }
            }
        }
    }

    private companion object {
        const val MAX_SNAPSHOT_CACHE_ENTRIES = 8
    }
}

internal fun buildMediaGalleryFolders(files: List<FileModel>): List<MediaGalleryFolder> {
    val grouped = files.groupBy { file -> storageParentPath(file.reference) }
    return grouped.entries
        .sortedByDescending { it.value.size }
        .map { (path, folderFiles) ->
            val lastModified = folderFiles.maxOfOrNull { it.lastModified } ?: 0L
            MediaGalleryFolder(
                path = path,
                label = path?.let(::storagePathName)?.ifBlank { path } ?: "Unknown",
                count = folderFiles.size,
                lastModified = lastModified
            )
        }
}
