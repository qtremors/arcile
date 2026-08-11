package dev.qtremors.arcile.core.operation

import dev.qtremors.arcile.core.storage.domain.CanonicalStorageIdentity
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageVolumeId
import kotlinx.serialization.Serializable

@Serializable
data class OperationStorageNodeRef(
    val backendId: String,
    val volumeId: String? = null,
    val displayPath: String,
    val canonicalIdentity: String,
    val contentUri: String? = null,
    val backendIdentity: String? = null,
    val capabilities: OperationStorageNodeCapabilities = OperationStorageNodeCapabilities()
) {
    fun toStorageNodeRef(): StorageNodeRef = StorageNodeRef(
        backendId = backendId,
        volumeId = volumeId?.takeIf(String::isNotBlank)?.let(StorageVolumeId::of),
        displayPath = StorageNodePath.of(displayPath),
        canonicalIdentity = CanonicalStorageIdentity.of(canonicalIdentity),
        capabilities = capabilities.toDomain(),
        contentUri = contentUri,
        backendIdentity = backendIdentity
    )

    companion object {
        fun from(ref: StorageNodeRef): OperationStorageNodeRef = OperationStorageNodeRef(
            backendId = ref.backendId,
            volumeId = ref.volumeId?.value,
            displayPath = ref.displayPath.absolutePath,
            canonicalIdentity = ref.canonicalIdentity.value,
            contentUri = ref.contentUri,
            backendIdentity = ref.backendIdentity,
            capabilities = OperationStorageNodeCapabilities.from(ref.capabilities)
        )
    }
}

@Serializable
data class OperationStorageNodeCapabilities(
    val canRead: Boolean = true,
    val canWrite: Boolean = true,
    val canDelete: Boolean = true,
    val canTrash: Boolean = false,
    val canArchive: Boolean = true,
    val canRename: Boolean = canWrite,
    val canCopy: Boolean = canRead,
    val canMove: Boolean = canWrite,
    val canExport: Boolean = canRead,
    val canShare: Boolean = canRead,
    val canOpenWith: Boolean = canRead
) {
    fun toDomain() = StorageNodeCapabilities(
        canRead = canRead,
        canWrite = canWrite,
        canDelete = canDelete,
        canTrash = canTrash,
        canArchive = canArchive,
        canRename = canRename,
        canCopy = canCopy,
        canMove = canMove,
        canExport = canExport,
        canShare = canShare,
        canOpenWith = canOpenWith
    )

    companion object {
        fun from(capabilities: StorageNodeCapabilities) = OperationStorageNodeCapabilities(
            canRead = capabilities.canRead,
            canWrite = capabilities.canWrite,
            canDelete = capabilities.canDelete,
            canTrash = capabilities.canTrash,
            canArchive = capabilities.canArchive,
            canRename = capabilities.canRename,
            canCopy = capabilities.canCopy,
            canMove = capabilities.canMove,
            canExport = capabilities.canExport,
            canShare = capabilities.canShare,
            canOpenWith = capabilities.canOpenWith
        )
    }
}
