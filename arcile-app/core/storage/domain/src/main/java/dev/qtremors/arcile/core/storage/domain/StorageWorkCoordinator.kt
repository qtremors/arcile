package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.StateFlow

interface StorageWorkCoordinator {
    val isMutationActive: StateFlow<Boolean>
    fun beginMutation()
    fun endMutation()
    suspend fun awaitLowPrioritySlot()
}
