package dev.qtremors.arcile.core.storage.data

import kotlinx.coroutines.CancellationException

internal fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

internal inline fun <T> runCatchingPreservingCancellation(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: Throwable) {
        error.rethrowIfCancellation()
        Result.failure(error)
    }
