package dev.qtremors.arcile.core.storage.data

import android.app.RecoverableSecurityException
import dev.qtremors.arcile.core.runtime.NativeStorageAuthorizationGateway
import dev.qtremors.arcile.core.storage.data.manager.TrashManager
import dev.qtremors.arcile.core.storage.domain.FileOperationProgress
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationOperation
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationRequirement
import dev.qtremors.arcile.core.storage.domain.StorageMutationResult
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.toStorageMutationResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import dev.qtremors.arcile.core.storage.domain.RestoredTrashItem
import dev.qtremors.arcile.core.storage.domain.PartialTrashRestoreException
import dev.qtremors.arcile.core.storage.domain.TrashRestoreBatch

class DefaultTrashRepository(
    private val trashManager: TrashManager,
    private val authorizationGateway: NativeStorageAuthorizationGateway
) : TrashRepository {
    override suspend fun moveToTrash(
        paths: List<String>,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<Unit> = trashManager.moveToTrash(paths, onProgress)

    override suspend fun restoreFromTrash(
        trashIds: List<String>,
        destinationPath: String?
    ): StorageMutationResult = trashManager.restoreFromTrash(trashIds, destinationPath)
        .toAuthorizedResult(StorageAuthorizationOperation.RESTORE_TRASH)

    override suspend fun restoreWithResults(trashIds: List<String>, destinationPath: String?): TrashRestoreBatch {
        val result = trashManager.restoreWithResults(trashIds, destinationPath)
        val error = result.exceptionOrNull()
        val partial = error as? PartialTrashRestoreException
        val outcome = if (error == null) StorageMutationResult.Completed else
            Result.failure<Unit>(partial?.cause ?: error).toAuthorizedResult(StorageAuthorizationOperation.RESTORE_TRASH)
        return TrashRestoreBatch(outcome, result.getOrNull() ?: partial?.restored.orEmpty())
    }

    override suspend fun undoRestore(items: List<RestoredTrashItem>): Result<Unit> = trashManager.undoRestore(items)

    override suspend fun emptyTrash(): StorageMutationResult =
        trashManager.emptyTrash().toAuthorizedResult(StorageAuthorizationOperation.EMPTY_TRASH)

    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> =
        trashManager.getTrashFiles()

    override suspend fun deletePermanentlyFromTrash(
        trashIds: List<String>
    ): StorageMutationResult = trashManager.deletePermanentlyFromTrash(trashIds)
        .toAuthorizedResult(StorageAuthorizationOperation.DELETE_TRASH)

    private fun Result<Unit>.toAuthorizedResult(
        operation: StorageAuthorizationOperation
    ): StorageMutationResult {
        val failure = exceptionOrNull() ?: return StorageMutationResult.Completed
        if (failure is CancellationException) throw failure
        val recoverable = failure as? RecoverableSecurityException
            ?: return toStorageMutationResult()
        val requirement = StorageAuthorizationRequirement(
            requestId = UUID.randomUUID().toString(),
            operation = operation
        )
        authorizationGateway.register(
            requirement,
            recoverable.userAction.actionIntent.intentSender
        )
        return StorageMutationResult.AuthorizationRequired(requirement)
    }
}
