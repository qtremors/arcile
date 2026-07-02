package dev.qtremors.arcile.core.storage.domain

class NativeConfirmationRequiredException(
    val intentSender: Any
) : Exception("Native confirmation required")

class DestinationRequiredException(
    val trashIds: List<String>
) : Exception("Destination directory required for restoration")

interface TrashRepository {
    suspend fun moveToTrash(
        paths: List<String>,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun restoreFromTrash(
        trashIds: List<String>,
        destinationPath: String? = null
    ): Result<Unit>
    suspend fun emptyTrash(): Result<Unit>
    suspend fun getTrashFiles(): Result<List<TrashMetadata>>
    suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit>
}

/**
 * Compatibility facade while features migrate toward narrow storage capabilities.
 */
interface FileRepository :
    FileBrowserRepository,
    SelectionPropertiesRepository,
    FileMutationRepository,
    SearchRepository,
    StorageAnalyticsRepository,
    TrashRepository,
    ArchiveRepository,
    VolumeRepository,
    ClipboardRepository
