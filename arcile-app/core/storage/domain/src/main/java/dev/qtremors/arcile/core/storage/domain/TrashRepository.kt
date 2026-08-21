package dev.qtremors.arcile.core.storage.domain

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
    ): StorageMutationResult
    suspend fun emptyTrash(): StorageMutationResult
    suspend fun getTrashFiles(): Result<List<TrashMetadata>>
    suspend fun deletePermanentlyFromTrash(trashIds: List<String>): StorageMutationResult
}
