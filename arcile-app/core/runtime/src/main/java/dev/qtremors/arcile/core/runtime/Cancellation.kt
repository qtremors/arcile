package dev.qtremors.arcile.core.runtime

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for coroutine code: failures become [Result.failure], but cancellation is rethrown
 * so leaving a screen or cancelling an action ends the work instead of being reported as an error.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }
