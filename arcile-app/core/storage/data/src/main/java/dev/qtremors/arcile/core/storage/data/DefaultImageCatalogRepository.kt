package dev.qtremors.arcile.core.storage.data

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.data.db.StorageNodeDao
import dev.qtremors.arcile.core.storage.data.db.StorageNodeEntity
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.appendVolumeSelection
import dev.qtremors.arcile.core.storage.data.source.mediaProjection
import dev.qtremors.arcile.core.storage.data.source.readMediaStoreFileRow
import dev.qtremors.arcile.core.storage.data.source.rowMatchesScope
import dev.qtremors.arcile.core.storage.data.util.indexedVolumesForScope
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ImageCatalogItem
import dev.qtremors.arcile.core.storage.domain.ImageCatalogRepository
import dev.qtremors.arcile.core.storage.domain.ImageCatalogSnapshot
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.di.ArcileDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultImageCatalogRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val volumeProvider: VolumeProvider,
    private val storageNodeDao: StorageNodeDao,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : ImageCatalogRepository {

    override suspend fun loadImages(volumeId: String?, forceRefresh: Boolean): Result<ImageCatalogSnapshot> =
        withContext(dispatchers.io) {
            runCatching {
                val normalizedVolumeId = volumeId?.takeIf { it.isNotBlank() }
                if (!forceRefresh) {
                    val cached = cachedImages(normalizedVolumeId)
                    if (cached.isNotEmpty()) {
                        return@runCatching ImageCatalogSnapshot(cached, isStale = false)
                    }
                }

                refreshImages(normalizedVolumeId)
                ImageCatalogSnapshot(cachedImages(normalizedVolumeId), isStale = false)
            }
        }

    override fun invalidate(paths: Collection<String>) {
        // Synchronous callers should not block on cache cleanup. A following force refresh
        // rewrites the image rows, while exact path invalidation is handled by mutation flows.
    }

    private suspend fun cachedImages(volumeId: String?): List<ImageCatalogItem> =
        storageNodeDao
            .listImages(volumeId, FileCategories.Images.extensions.toList())
            .map { it.toImageCatalogItem() }

    private suspend fun refreshImages(volumeId: String?) {
        val scope: StorageScope = StorageScope.Category(volumeId, FileCategories.Images.name)
        val allVolumes = volumeProvider.currentVolumes()
        val volumes = indexedVolumesForScope(scope, allVolumes)
        if (scope !is StorageScope.AllStorage && volumes.isEmpty()) {
            storageNodeDao.deleteImages(volumeId, FileCategories.Images.extensions.toList())
            return
        }

        val uri = MediaStore.Files.getContentUri("external")
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        val imageClauses = mutableListOf("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
        selectionArgs += "image/%"
        FileCategories.Images.extensions.forEach { extension ->
            imageClauses += "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            selectionArgs += "%.$extension"
        }
        selectionParts += imageClauses.joinToString(separator = " OR ", prefix = "(", postfix = ")")
        appendVolumeSelection(selectionParts, selectionArgs, volumes)

        val rows = mutableListOf<StorageNodeEntity>()
        val now = System.currentTimeMillis()
        context.contentResolver.query(
            uri,
            mediaProjection(),
            selectionParts.joinToString(" AND "),
            selectionArgs.toTypedArray(),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val row = cursor.readMediaStoreFileRow()
                if (rowMatchesScope(row, scope, volumes) &&
                    FileCategories.getCategoryForFile(row.extension, row.mimeType) == FileCategories.Images
                ) {
                    rows += row.toStorageNodeEntity(volumes, now)
                }
            }
        }

        storageNodeDao.deleteImages(volumeId, FileCategories.Images.extensions.toList())
        if (rows.isNotEmpty()) {
            storageNodeDao.upsert(rows.distinctBy { it.path })
        }
    }

    private fun dev.qtremors.arcile.core.storage.data.source.MediaStoreFileRow.toStorageNodeEntity(
        volumes: List<dev.qtremors.arcile.core.storage.domain.StorageVolume>,
        scannedAt: Long
    ): StorageNodeEntity {
        val file = toFileModel(volumes)
        return StorageNodeEntity(
            path = file.absolutePath,
            parentPath = File(file.absolutePath).parent,
            name = file.name,
            extension = file.extension.lowercase(),
            mimeType = file.mimeType,
            sizeBytes = file.size,
            lastModified = file.lastModified,
            isDirectory = false,
            isHidden = file.isHidden,
            contentUri = file.nodeRef.contentUri,
            mediaStoreId = id.takeIf { it > 0L },
            mediaStoreVolume = volumeName,
            volumeId = file.nodeRef.volumeId?.value,
            width = width,
            height = height,
            dateAdded = dateAdded,
            scannedAt = scannedAt,
            stale = false
        )
    }

    private fun StorageNodeEntity.toImageCatalogItem(): ImageCatalogItem =
        ImageCatalogItem(
            file = FileModel(
                name = name,
                absolutePath = path,
                size = sizeBytes,
                lastModified = lastModified,
                isDirectory = isDirectory,
                extension = extension,
                isHidden = isHidden,
                mimeType = mimeType,
                nodeRef = contentUri?.let { uri ->
                    StorageNodeRef.mediaStore(
                        id = mediaStoreId ?: 0L,
                        volumeName = mediaStoreVolume,
                        contentUri = uri,
                        displayPath = path,
                        volumeId = volumeId,
                        localPath = path
                    )
                } ?: StorageNodeRef.local(path = path, volumeId = volumeId)
            ),
            width = width,
            height = height
        )
}
