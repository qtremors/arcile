package dev.qtremors.arcile.domain

/**
 * Metadata record for a file or directory that has been moved to the trash.
 *
 * Trash entries are stored as JSON sidecar files inside `.arcile_trash/` on external storage.
 * The actual file data is stored alongside with a name derived from [id].
 *
 * > **Security note:** Trash is stored on shared external storage and is readable by any
 * > app holding `MANAGE_EXTERNAL_STORAGE`. See TASKS.md B3.
 *
 * @property id Unique identifier for this trash entry (used to locate the trashed blob).
 * @property originalPath Absolute path the file occupied before being trashed.
 * @property deletionTime Unix epoch millisecond timestamp when the file was moved to trash.
 * @property fileModel Snapshot of the file's metadata at the time of deletion.
 */
data class TrashMetadata(
    val id: String,
    val originalPath: String,
    val deletionTime: Long,
    val fileModel: FileModel
)
