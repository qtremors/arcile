package dev.qtremors.arcile.core.storage.domain

enum class StorageAuthorizationOperation {
    RESTORE_TRASH,
    EMPTY_TRASH,
    DELETE_TRASH
}

data class StorageAuthorizationRequirement(
    val requestId: String,
    val operation: StorageAuthorizationOperation
)

sealed interface StorageMutationResult {
    data object Completed : StorageMutationResult
    data class AuthorizationRequired(
        val requirement: StorageAuthorizationRequirement
    ) : StorageMutationResult
    data class Failed(val error: Throwable) : StorageMutationResult

}

inline fun StorageMutationResult.onSuccess(action: () -> Unit): StorageMutationResult {
    if (this === StorageMutationResult.Completed) action()
    return this
}

inline fun StorageMutationResult.onAuthorizationRequired(
    action: (StorageAuthorizationRequirement) -> Unit
): StorageMutationResult {
    if (this is StorageMutationResult.AuthorizationRequired) action(requirement)
    return this
}

inline fun StorageMutationResult.onFailure(
    action: (Throwable) -> Unit
): StorageMutationResult {
    if (this is StorageMutationResult.Failed) action(error)
    return this
}

fun Result<Unit>.toStorageMutationResult(): StorageMutationResult = fold(
    onSuccess = { StorageMutationResult.Completed },
    onFailure = StorageMutationResult::Failed
)
