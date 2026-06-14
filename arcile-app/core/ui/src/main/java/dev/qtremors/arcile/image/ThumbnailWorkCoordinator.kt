package dev.qtremors.arcile.image

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object ThumbnailWorkCoordinator {
    private val expensiveSemaphore = Semaphore(EXPENSIVE_CONCURRENCY)

    suspend fun <T> withExpensivePermit(block: suspend () -> T): T =
        expensiveSemaphore.withPermit { block() }

    private const val EXPENSIVE_CONCURRENCY = 4
}

