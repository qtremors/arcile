package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class StorageMutationEvent(
    val paths: List<String>
)

interface StorageMutationNotifier {
    val events: Flow<StorageMutationEvent>
    fun notify(paths: Collection<String>)
}

object NoOpStorageMutationNotifier : StorageMutationNotifier {
    override val events: Flow<StorageMutationEvent> = emptyFlow()
    override fun notify(paths: Collection<String>) = Unit
}
