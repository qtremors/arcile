package dev.qtremors.arcile.feature.gallery

import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import androidx.compose.runtime.Composable
import dev.qtremors.arcile.core.ui.PasteConflictDialog
import dev.qtremors.arcile.core.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import dev.qtremors.arcile.core.ui.dialogs.BatchRenameDialog
import dev.qtremors.arcile.core.storage.domain.storagePathName

@Composable
internal fun MediaGalleryDialogs(
    state: MediaGalleryState,
    currentTab: CategoryLibraryPage,
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

    if (showRenameDialog && state.selectedFiles.size > 1) {
        val selectedModels = state.files.filter { it.reference in state.selectedFiles }
        BatchRenameDialog(
            files = selectedModels,
            onDismiss = {
                onDismissRenameDialog()
                selectionActions.clear()
            },
            onConfirm = { renames ->
                fileActions.batchRename(renames)
                onDismissRenameDialog()
            },
            existingFolderNames = state.files.mapTo(mutableSetOf()) { it.name }
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
        GalleryViewOptionsSheet(
            currentTab = currentTab,
            isVideoGallery = state.isVideoGallery,
            itemPresentation = state.presentation,
            folderPresentation = state.folderPresentation,
            isAspectRatio = state.isAspectRatio,
            grouping = state.grouping,
            showFileDetails = state.showFileDetails,
            onItemsPresentationChange = presentationActions.itemsChange,
            onFolderPresentationChange = presentationActions.foldersChange,
            onItemsAspectRatioChange = presentationActions.aspectRatioChange,
            onGroupingChange = presentationActions.groupingChange,
            onShowFileDetailsChange = presentationActions.showFileDetailsChange,
            onDismiss = onDismissPresentationSheet
        )
    }
}
