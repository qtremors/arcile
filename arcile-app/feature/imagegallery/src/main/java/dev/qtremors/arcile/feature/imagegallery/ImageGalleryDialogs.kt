package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.runtime.Composable
import dev.qtremors.arcile.core.ui.PasteConflictDialog
import dev.qtremors.arcile.core.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import dev.qtremors.arcile.core.storage.domain.storagePathName

@Composable
internal fun ImageGalleryDialogs(
    state: ImageGalleryState,
    currentTab: GalleryTab,
    showRenameDialog: Boolean,
    showClipboardContents: Boolean,
    showPresentationSheet: Boolean,
    selectionActions: GallerySelectionActions,
    deleteActions: GalleryDeleteActions,
    clipboardActions: GalleryClipboardActions,
    fileActions: GalleryFileActions,
    presentationActions: GalleryPresentationActions,
    onDismissRenameDialog: () -> Unit,
    onDismissClipboardContents: () -> Unit,
    onDismissPresentationSheet: () -> Unit
) {
    if (showRenameDialog && state.selectedFiles.size == 1) {
        val selectedPath = state.selectedFiles.first()
        RenameDialog(
            currentName = storagePathName(selectedPath),
            onDismiss = {
                onDismissRenameDialog()
                selectionActions.clear()
            },
            onConfirm = { newName ->
                fileActions.rename(selectedPath, newName)
                onDismissRenameDialog()
            }
        )
    }

    if (
        state.showTrashConfirmation ||
        state.showPermanentDeleteConfirmation ||
        state.showMixedDeleteExplanation
    ) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedFiles.size,
            isPermanentDeleteChecked =
                state.isPermanentDeleteChecked || state.showMixedDeleteExplanation,
            isPermanentDeleteToggleEnabled =
                state.isPermanentDeleteToggleEnabled && !state.showMixedDeleteExplanation,
            onConfirm = if (state.showMixedDeleteExplanation) ({}) else deleteActions.confirm,
            onDismiss = deleteActions.dismiss,
            onTogglePermanentDelete = deleteActions.togglePermanent,
            decision = state.deleteDecision,
            isShredChecked = state.isShredChecked,
            onToggleShred = deleteActions.toggleShred
        )
    }

    if (state.showConflictDialog && state.pasteConflicts.isNotEmpty()) {
        PasteConflictDialog(
            conflicts = state.pasteConflicts,
            onResolve = clipboardActions.resolveConflicts,
            onDismiss = clipboardActions.dismissConflictDialog
        )
    }

    state.clipboardState?.let { clipboardState ->
        if (showClipboardContents) {
            ClipboardContentsDialog(
                state = clipboardState,
                onRemoveItem = clipboardActions.remove,
                onDismiss = onDismissClipboardContents
            )
        }
    }

    if (state.isPropertiesVisible) {
        PropertiesDialog(
            properties = state.properties,
            isLoading = state.isPropertiesLoading,
            onDismiss = {
                selectionActions.dismissProperties()
                selectionActions.clear()
            }
        )
    }

    if (showPresentationSheet) {
        GalleryViewOptionsDialog(
            currentTab = currentTab,
            photosPresentation = state.presentation,
            albumPresentation = state.albumPresentation,
            isAspectRatio = state.isAspectRatio,
            grouping = state.imageGalleryGrouping,
            showFileDetails = state.showFileDetails,
            onPhotosPresentationChange = presentationActions.photosChange,
            onAlbumPresentationChange = presentationActions.albumsChange,
            onPhotosAspectRatioChange = presentationActions.aspectRatioChange,
            onGroupingChange = presentationActions.groupingChange,
            onShowFileDetailsChange = presentationActions.showFileDetailsChange,
            onDismiss = onDismissPresentationSheet
        )
    }
}
