package dev.qtremors.arcile.feature.home

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.StorageClassificationStore
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.ui.R
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal sealed interface HomeClassificationUpdate {
    data class Dismiss(val storageKey: String) : HomeClassificationUpdate
    data class Restore(
        val storageKey: String,
        val error: UiText
    ) : HomeClassificationUpdate
}

internal class HomeClassificationController(
    private val scope: CoroutineScope,
    private val repository: StorageClassificationStore,
    private val findVolume: (String) -> StorageVolume?,
    private val onUpdate: (HomeClassificationUpdate) -> Unit
) {
    private val suppressedVolumeKeys = ConcurrentHashMap.newKeySet<String>()

    fun unsuppressed(volumes: List<StorageVolume>): List<StorageVolume> =
        volumes.filterNot { suppressedVolumeKeys.contains(it.storageKey) }

    fun classify(storageKey: String, kind: StorageKind) {
        onUpdate(HomeClassificationUpdate.Dismiss(storageKey))
        scope.launch {
            try {
                val volume = findVolume(storageKey)
                repository.setClassification(
                    storageKey = storageKey,
                    kind = kind,
                    lastSeenName = volume?.name,
                    lastSeenPath = volume?.path
                )
                suppressedVolumeKeys.remove(storageKey)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                onUpdate(
                    HomeClassificationUpdate.Restore(
                        storageKey = storageKey,
                        error = UiText.StringResource(
                            R.string.error_save_classification_failed,
                            listOf(error.message.orEmpty())
                        )
                    )
                )
            }
        }
    }

    fun reset(storageKey: String) {
        scope.launch {
            repository.resetClassification(storageKey)
            suppressedVolumeKeys.remove(storageKey)
        }
    }

    fun suppress(storageKey: String) {
        suppressedVolumeKeys.add(storageKey)
        onUpdate(HomeClassificationUpdate.Dismiss(storageKey))
    }
}
