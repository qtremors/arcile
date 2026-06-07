package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.StorageWorkCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

object NoOpStorageWorkCoordinator : StorageWorkCoordinator {
    private val inactive = MutableStateFlow(false)
    override val isMutationActive: StateFlow<Boolean> = inactive.asStateFlow()
    override fun beginMutation() = Unit
    override fun endMutation() = Unit
    override suspend fun awaitLowPrioritySlot() = Unit
}

@Singleton
class DefaultStorageWorkCoordinator @Inject constructor() : StorageWorkCoordinator {
    private val activeMutationCount = AtomicInteger(0)
    private val _isMutationActive = MutableStateFlow(false)
    override val isMutationActive: StateFlow<Boolean> = _isMutationActive.asStateFlow()

    override fun beginMutation() {
        _isMutationActive.value = activeMutationCount.incrementAndGet() > 0
    }

    override fun endMutation() {
        val remaining = activeMutationCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        _isMutationActive.value = remaining > 0
    }

    override suspend fun awaitLowPrioritySlot() {
        while (_isMutationActive.value) {
            delay(250)
        }
    }
}
