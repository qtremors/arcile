package dev.qtremors.arcile.core.storage.domain

enum class TrashRestoreStatus {
    ORIGINAL_AVAILABLE,
    ORIGINAL_CONFLICT_RENAME,
    DESTINATION_REQUIRED,
    RECOVERED_ITEM,
    BACKEND_UNAVAILABLE
}

/**
 * Metadata record for a file or directory that has been moved to the trash.
 *
 * Local trash entries use JSON sidecars on the source volume. Privileged entries keep their
 * recovery metadata in app-private storage while their payload remains in the source volume's
 * backend-owned trash directory.
 *
 * @property id Unique identifier for this trash entry (used to locate the trashed blob).
 * @property originalPath Absolute path the file occupied before being trashed.
 * @property deletionTime Unix epoch millisecond timestamp when the file was moved to trash.
 * @property fileModel Snapshot of the file's metadata at the time of deletion.
 * @property sourceVolumeId The ID of the storage volume this item was deleted from.
 * @property sourceStorageKind The kind of storage this item was deleted from.
 */
data class TrashMetadata(
    val id: String,
    val originalPath: String,
    val deletionTime: Long,
    val fileModel: FileModel,
    val sourceVolumeId: String,
    val sourceStorageKind: StorageKind,
    val restoreStatus: TrashRestoreStatus = TrashRestoreStatus.ORIGINAL_AVAILABLE
) {
    val trashItemId: TrashItemId get() = TrashItemId.of(id)
    val originalNodePath: StorageNodePath? get() = originalPath.takeIf { it.isNotBlank() }?.let(StorageNodePath::of)
    val deletedAt: EpochMillis get() = EpochMillis.of(deletionTime)
    val typedSourceVolumeId: StorageVolumeId get() = StorageVolumeId.of(sourceVolumeId)
}
