package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.StorageMutationResult
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.toStorageMutationResult

class FakeTrashRepository : TrashRepository {
    var moveToTrashResultProvider:
        (suspend (List<String>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null
    var restoreFromTrashResultProvider:
        (suspend (List<String>, String?) -> Result<Unit>)? = null
    var restoreFromTrashMutationResultProvider:
        (suspend (List<String>, String?) -> StorageMutationResult)? = null
    var emptyTrashResult: Result<Unit> = Result.failure(NotImplementedError())
    var trashFilesResult: Result<List<TrashMetadata>> = Result.failure(NotImplementedError())
    var deletePermanentlyFromTrashResult: Result<Unit> = Result.failure(NotImplementedError())

    val moveToTrashRequests = mutableListOf<List<String>>()
    val restoreFromTrashRequests = mutableListOf<RestoreRequest>()
    val emptyTrashCalls = mutableListOf<Unit>()
    val deletePermanentlyFromTrashRequests = mutableListOf<List<String>>()

    data class RestoreRequest(val trashIds: List<String>, val destinationPath: String?)

    override suspend fun moveToTrash(
        paths: List<String>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        moveToTrashRequests += paths
        return moveToTrashResultProvider?.invoke(paths, onProgress) ?: Result.success(Unit)
    }

    override suspend fun restoreFromTrash(
        trashIds: List<String>,
        destinationPath: String?
    ): StorageMutationResult {
        restoreFromTrashRequests += RestoreRequest(trashIds, destinationPath)
        return restoreFromTrashMutationResultProvider?.invoke(trashIds, destinationPath)
            ?: restoreFromTrashResultProvider?.invoke(trashIds, destinationPath)?.toStorageMutationResult()
            ?: StorageMutationResult.Completed
    }

    override suspend fun emptyTrash(): StorageMutationResult {
        emptyTrashCalls += Unit
        return emptyTrashResult.toStorageMutationResult()
    }

    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = trashFilesResult

    override suspend fun deletePermanentlyFromTrash(
        trashIds: List<String>
    ): StorageMutationResult {
        deletePermanentlyFromTrashRequests += trashIds
        return deletePermanentlyFromTrashResult.toStorageMutationResult()
    }
}
