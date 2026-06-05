package dev.qtremors.arcile.core.storage.domain

data class BatchMutationResult(
    val succeededPaths: List<String> = emptyList(),
    val skippedPaths: List<String> = emptyList(),
    val failedItems: List<BatchMutationFailure> = emptyList(),
    val cleanupRequiredPaths: List<String> = emptyList()
) {
    val isCompleteSuccess: Boolean
        get() = failedItems.isEmpty() && cleanupRequiredPaths.isEmpty()

    fun requireCompleteSuccess(operationName: String): Result<Unit> =
        if (isCompleteSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(toPartialFailure(operationName))
        }

    fun toPartialFailure(operationName: String): BatchMutationPartialFailure {
        val failedDescription = failedItems.firstOrNull()?.let { failure ->
            " First failure: ${failure.displayName}: ${failure.message}"
        }.orEmpty()
        val message = "$operationName partially completed: ${succeededPaths.size} succeeded, " +
            "${skippedPaths.size} skipped, ${failedItems.size} failed.$failedDescription"
        return BatchMutationPartialFailure(this, message)
    }
}

data class BatchMutationFailure(
    val path: String,
    val displayName: String,
    val message: String,
    val causeType: String,
    val cleanupRequired: Boolean = false
)

class BatchMutationPartialFailure(
    val batchResult: BatchMutationResult,
    message: String
) : Exception(message)
