package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageScope
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject

interface ImageGalleryRepository {
    val mutationEvents: Flow<dev.qtremors.arcile.core.storage.domain.StorageMutationEvent>
    suspend fun loadImages(volumeId: String?, forceRefresh: Boolean = false): ImageGallerySnapshot
    fun invalidate(paths: Collection<String> = emptyList())
}

data class ImageGallerySnapshot(
    val files: List<FileModel>,
    val albums: List<ImageGalleryAlbum>,
    val isStale: Boolean
)

data class ImageGalleryAlbum(
    val path: String?,
    val label: String,
    val count: Int
)

class DefaultImageGalleryRepository @Inject constructor(
    private val searchRepository: SearchRepository,
    private val storageMutationNotifier: StorageMutationNotifier
) : ImageGalleryRepository {
    private val snapshotLock = Any()
    private val snapshots = LinkedHashMap<String, ImageGallerySnapshot>()

    override val mutationEvents = storageMutationNotifier.events

    override suspend fun loadImages(volumeId: String?, forceRefresh: Boolean): ImageGallerySnapshot {
        val key = volumeId.orEmpty()
        val cached = synchronized(snapshotLock) { snapshots[key] }
        if (cached != null && !forceRefresh) return cached.copy(isStale = true)

        val scope = StorageScope.Category(volumeId?.takeIf { it.isNotBlank() }, FileCategories.Images.name)
        val files = searchRepository.getFilesByCategory(scope, FileCategories.Images.name)
            .getOrDefault(emptyList())
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified }
        val snapshot = ImageGallerySnapshot(
            files = files,
            albums = buildAlbums(files),
            isStale = false
        )
        synchronized(snapshotLock) { snapshots[key] = snapshot }
        return snapshot
    }

    override fun invalidate(paths: Collection<String>) {
        synchronized(snapshotLock) {
            if (paths.isEmpty()) {
                snapshots.clear()
                return
            }
            val affected = paths.map { it.replace('\\', '/') }
            snapshots.entries.removeIf { entry ->
                entry.value.files.any { file ->
                    val normalized = file.absolutePath.replace('\\', '/')
                    affected.any { changed -> normalized == changed || normalized.startsWith("$changed/") || changed.startsWith("${normalized}/") }
                }
            }
        }
    }

    private fun buildAlbums(files: List<FileModel>): List<ImageGalleryAlbum> {
        val grouped = files.groupBy { file -> File(file.absolutePath).parent }
        return grouped.entries
            .sortedByDescending { it.value.size }
            .map { (path, albumFiles) ->
                ImageGalleryAlbum(
                    path = path,
                    label = path?.substringAfterLast(File.separatorChar)?.ifBlank { path } ?: "Unknown",
                    count = albumFiles.size
                )
            }
    }
}
