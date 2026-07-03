package dev.qtremors.arcile.core.storage.data.source

import android.webkit.MimeTypeMap
import dev.qtremors.arcile.core.storage.data.db.StorageNodeEntity
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import java.io.File

internal class LocalFileModelMapper {
    fun toFileModel(file: File): FileModel {
        val extension = file.extension
        val mimeType = extension.takeIf(String::isNotEmpty)
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
        return FileModel(
            name = file.name,
            absolutePath = file.absolutePath,
            size = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            isDirectory = file.isDirectory,
            extension = extension,
            isHidden = file.isHidden,
            mimeType = mimeType,
            nodeRef = StorageNodeRef.local(
                path = file.absolutePath,
                capabilities = StorageNodeCapabilities(
                    canRead = true,
                    canWrite = true,
                    canDelete = true,
                    canTrash = false,
                    canArchive = file.isFile
                )
            )
        )
    }

    fun toStorageNodeEntity(
        file: File,
        volumeId: String?,
        scannedAt: Long
    ): StorageNodeEntity = StorageNodeEntity(
        path = file.absolutePath,
        parentPath = file.parentFile?.absolutePath,
        name = file.name,
        extension = file.extension.lowercase(),
        mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()),
        sizeBytes = if (file.isFile) file.length() else 0L,
        lastModified = file.lastModified(),
        isDirectory = file.isDirectory,
        isHidden = file.isHidden,
        contentUri = null,
        mediaStoreId = null,
        mediaStoreVolume = null,
        volumeId = volumeId,
        width = null,
        height = null,
        dateAdded = scannedAt,
        scannedAt = scannedAt
    )

    fun toFileModel(entity: StorageNodeEntity): FileModel = FileModel(
        name = entity.name,
        absolutePath = entity.path,
        size = entity.sizeBytes,
        lastModified = entity.lastModified,
        isDirectory = entity.isDirectory,
        extension = entity.extension.orEmpty(),
        isHidden = entity.isHidden,
        mimeType = entity.mimeType,
        nodeRef = StorageNodeRef.local(
            path = entity.path,
            capabilities = StorageNodeCapabilities(
                canRead = true,
                canWrite = true,
                canDelete = true,
                canTrash = false,
                canArchive = !entity.isDirectory
            )
        )
    )
}
