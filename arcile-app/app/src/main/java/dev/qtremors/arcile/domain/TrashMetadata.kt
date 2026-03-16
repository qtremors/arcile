package dev.qtremors.arcile.domain

/**
 * Metadata record for a file or directory that has been moved to the trash.
 *
 * Trash entries are stored as JSON sidecar files inside `.arcile/.trash` on external storage.
 * The actual file data is stored alongside with a name derived from [id].
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
    val sourceStorageKind: StorageKind
)
