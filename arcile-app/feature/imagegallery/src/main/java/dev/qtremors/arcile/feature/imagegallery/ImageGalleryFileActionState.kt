package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.presentation.OperationUiState
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.presentation.PropertiesUiModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

internal data class ImageGalleryFileActionState(
    val selectedFiles: PersistentSet<String> = persistentSetOf(),
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val isShredChecked: Boolean = false,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: PropertiesUiModel? = null,
    val clipboardState: ClipboardState? = null,
    val activeFileOperation: OperationUiState? = null,
    val pasteConflicts: PersistentList<FileConflict> = persistentListOf(),
    val showConflictDialog: Boolean = false,
    val pasteDestinationAlbumPath: String? = null
)
