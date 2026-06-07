package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.StorageMutationEvent
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultStorageMutationNotifier @Inject constructor() : StorageMutationNotifier {
    private val _events = MutableSharedFlow<StorageMutationEvent>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events = _events.asSharedFlow()

    override fun notify(paths: Collection<String>) {
        _events.tryEmit(StorageMutationEvent(paths.distinct()))
    }
}
