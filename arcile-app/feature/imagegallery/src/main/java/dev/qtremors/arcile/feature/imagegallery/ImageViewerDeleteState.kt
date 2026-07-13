package dev.qtremors.arcile.feature.imagegallery

import kotlinx.collections.immutable.PersistentSet

internal fun ImageViewerState.withDeleteDialogsHidden(
    selection: PersistentSet<String> = selectedFiles
): ImageViewerState = copy(
    showTrashConfirmation = false,
    showPermanentDeleteConfirmation = false,
    showMixedDeleteExplanation = false,
    deleteDecision = null,
    isShredChecked = false,
    selectedFiles = selection
)
