package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.StorageWorkCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    // The count and the published flag change together under one lock; updated separately, an
    // ending mutation could publish "idle" after another had already begun.
    private val lock = Any()
    private var activeMutationCount = 0
    private val _isMutationActive = MutableStateFlow(false)
    override val isMutationActive: StateFlow<Boolean> = _isMutationActive.asStateFlow()

    override fun beginMutation() = synchronized(lock) {
        activeMutationCount++
        _isMutationActive.value = true
    }

    override fun endMutation() = synchronized(lock) {
        activeMutationCount = (activeMutationCount - 1).coerceAtLeast(0)
        _isMutationActive.value = activeMutationCount > 0
    }

    /** Suspends until no mutation is running, without waking up to check. */
    override suspend fun awaitLowPrioritySlot() {
        _isMutationActive.first { active -> !active }
    }
}
