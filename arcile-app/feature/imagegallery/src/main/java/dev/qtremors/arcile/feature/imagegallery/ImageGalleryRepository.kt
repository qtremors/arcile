package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ImageCatalogRepository
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.storage.domain.storagePathName
import dev.qtremors.arcile.core.storage.domain.isStorageDescendantOrSelf
import dev.qtremors.arcile.core.storage.domain.normalizeStoragePath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal interface ImageGalleryRepository {
    val mutationEvents: Flow<dev.qtremors.arcile.core.storage.domain.StorageMutationEvent>
    suspend fun loadImages(volumeId: String?, forceRefresh: Boolean = false): ImageGallerySnapshot
    fun invalidate(paths: Collection<String> = emptyList())
}

internal data class ImageGallerySnapshot(
    val files: List<FileModel>,
    val albums: List<ImageGalleryAlbum>,
    val aspectRatios: Map<String, Float>,
    val isStale: Boolean
)

internal data class ImageGalleryAlbum(
    val path: String?,
    val label: String,
    val count: Int,
    val lastModified: Long
)

internal class DefaultImageGalleryRepository @Inject constructor(
    private val imageCatalogRepository: ImageCatalogRepository,
    private val storageMutationNotifier: StorageMutationNotifier,
    private val dispatchers: ArcileDispatchers
) : ImageGalleryRepository {
    private val snapshotLock = Any()
    private val snapshots = LinkedHashMap<String, ImageGallerySnapshot>()

    override val mutationEvents = storageMutationNotifier.events

    override suspend fun loadImages(volumeId: String?, forceRefresh: Boolean): ImageGallerySnapshot {
        val key = volumeId.orEmpty()
        val cached = synchronized(snapshotLock) { snapshots[key] }
        if (cached != null && !forceRefresh) return cached.copy(isStale = false)

        val catalog = imageCatalogRepository.loadImages(volumeId, forceRefresh).getOrThrow()
        val snapshot = withContext(dispatchers.default) {
            val files = catalog.items
                .map { it.file }
                .distinctBy { it.absolutePath }
                .sortedByDescending { it.lastModified }
            val aspectRatios = catalog.items
                .mapNotNull { item -> item.aspectRatio?.let { item.file.absolutePath to it } }
                .toMap()
            ImageGallerySnapshot(
                files = files,
                albums = buildImageGalleryAlbums(files),
                aspectRatios = aspectRatios,
                isStale = catalog.isStale
            )
        }
        synchronized(snapshotLock) { snapshots[key] = snapshot }
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
                    val normalized = normalizeStoragePath(file.absolutePath)
                    affected.any { changed ->
                        isStorageDescendantOrSelf(normalized, changed) ||
                            isStorageDescendantOrSelf(changed, normalized)
                    }
                }
            }
        }
    }

}

internal fun buildImageGalleryAlbums(files: List<FileModel>): List<ImageGalleryAlbum> {
    val grouped = files.groupBy { file -> storageParentPath(file.absolutePath) }
    return grouped.entries
        .sortedByDescending { it.value.size }
        .map { (path, albumFiles) ->
            val lastModified = albumFiles.maxOfOrNull { it.lastModified } ?: 0L
            ImageGalleryAlbum(
                path = path,
                label = path?.let(::storagePathName)?.ifBlank { path } ?: "Unknown",
                count = albumFiles.size,
                lastModified = lastModified
            )
        }
}
