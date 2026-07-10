package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ImageCatalogRepository
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import kotlinx.coroutines.Dispatchers
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
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
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
            val affected = paths.map { it.replace('\\', '/') }
            snapshots.entries.removeIf { entry ->
                entry.value.files.any { file ->
                    val normalized = file.absolutePath.replace('\\', '/')
                    affected.any { changed -> normalized == changed || normalized.startsWith("$changed/") || changed.startsWith("${normalized}/") }
                }
            }
        }
    }

}

internal fun buildImageGalleryAlbums(files: List<FileModel>): List<ImageGalleryAlbum> {
    val grouped = files.groupBy { file -> galleryParentPath(file.absolutePath) }
    return grouped.entries
        .sortedByDescending { it.value.size }
        .map { (path, albumFiles) ->
            val lastModified = albumFiles.maxOfOrNull { it.lastModified } ?: 0L
            ImageGalleryAlbum(
                path = path,
                label = path?.substringAfterLast('/')?.ifBlank { path } ?: "Unknown",
                count = albumFiles.size,
                lastModified = lastModified
            )
        }
}

internal fun galleryParentPath(path: String): String? {
    val normalized = path.replace('\\', '/')
    return normalized.substringBeforeLast('/', missingDelimiterValue = "").ifBlank { null }
}
