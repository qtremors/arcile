package dev.qtremors.arcile.core.storage.domain

class DestinationRequiredException(
    val trashIds: List<String>
) : Exception("Destination directory required for restoration")

data class RestoredTrashItem(
    val trashId: String,
    val path: String,
    val undoToken: String? = null,
    val renamedForConflict: Boolean = false
)

data class TrashRestoreBatch(val outcome: StorageMutationResult, val restored: List<RestoredTrashItem>)

class PartialTrashRestoreException(val restored: List<RestoredTrashItem>, cause: Throwable) : Exception(cause.message, cause)

interface TrashRepository {
    suspend fun moveToTrash(
        paths: List<String>,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun restoreFromTrash(
        trashIds: List<String>,
        destinationPath: String? = null
    ): StorageMutationResult
    suspend fun restoreWithResults(trashIds: List<String>, destinationPath: String? = null): TrashRestoreBatch =
        TrashRestoreBatch(restoreFromTrash(trashIds, destinationPath), emptyList())
    suspend fun undoRestore(items: List<RestoredTrashItem>): Result<Unit> =
        Result.failure(UnsupportedOperationException("Verified restore undo is unavailable"))
    suspend fun emptyTrash(): StorageMutationResult
    suspend fun getTrashFiles(): Result<List<TrashMetadata>>
    suspend fun deletePermanentlyFromTrash(trashIds: List<String>): StorageMutationResult
}
