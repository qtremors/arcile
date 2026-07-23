package dev.qtremors.arcile.feature.videoplayer

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

internal data class VideoViewerState(
    val isInitialized: Boolean = false,
    val files: PersistentList<FileModel> = persistentListOf(),
    val displayedFiles: PersistentList<FileModel> = persistentListOf(),
    val favoriteFiles: PersistentSet<String> = persistentSetOf(),
    val selectedFiles: PersistentSet<String> = persistentSetOf(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val isShredChecked: Boolean = false,
    val viewerSessionInitialPath: String? = null,
    val viewerCurrentPath: String? = null,
    val viewerMetadataPath: String? = null,
    val viewerUiVisible: Boolean = true,
    val viewerEraseDialogPath: String? = null
) {
    fun withDeleteDialogsHidden(selected: PersistentSet<String> = selectedFiles): VideoViewerState =
        copy(
            showTrashConfirmation = false,
            showPermanentDeleteConfirmation = false,
            showMixedDeleteExplanation = false,
            selectedFiles = selected
        )
}

internal fun <K, V> MutableMap<K, V>.putBounded(key: K, value: V, maxEntries: Int) {
    require(maxEntries > 0)
    if (key !in this && size >= maxEntries) keys.firstOrNull()?.let(::remove)
    this[key] = value
}

internal data class VideoSeekState(
    val progress: Float,
    val canSeek: Boolean
)

internal fun videoSeekState(position: Long, duration: Long): VideoSeekState {
    if (duration <= 0L) return VideoSeekState(progress = 0f, canSeek = false)
    return VideoSeekState(
        progress = (position.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f),
        canSeek = true
    )
}
