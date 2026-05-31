package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.shared.ui.PasteConflictDialog
import dev.qtremors.arcile.shared.ui.SearchFiltersBottomSheet
import dev.qtremors.arcile.shared.ui.SortOptionDialog
import dev.qtremors.arcile.shared.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.shared.ui.dialogs.CreateFakeFileDialog
import dev.qtremors.arcile.shared.ui.dialogs.CreateFileDialog
import dev.qtremors.arcile.shared.ui.dialogs.CreateFolderDialog
import dev.qtremors.arcile.shared.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.shared.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.shared.ui.dialogs.RenameDialog
import java.io.File

@Composable
internal fun BrowserDialogs(
    state: BrowserState,
    currentPresentation: BrowserPresentationPreferences,
    dialogVisibility: BrowserDialogVisibility,
    actions: BrowserUiActions
) {
    if (state.isSearchFilterMenuVisible) {
        SearchFiltersBottomSheet(
            currentFilters = state.activeSearchFilters,
            onApplyFilters = { actions.onSearchFiltersChange(it) },
            onDismiss = { actions.onToggleSearchFilterMenu(false) },
            showCategoryFilter = !state.isCategoryScreen
        )
    }

    if (dialogVisibility.showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { dialogVisibility.showCreateFolderDialog = false },
            onConfirm = { name ->
                actions.onCreateFolder(name)
                dialogVisibility.showCreateFolderDialog = false
            },
            existingNames = state.displayState.existingNames,
            destinationPath = state.currentPath
        )
    }

    if (state.showTrashConfirmation || state.showPermanentDeleteConfirmation) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedFiles.size,
            isPermanentDeleteChecked = state.isPermanentDeleteChecked,
            isPermanentDeleteToggleEnabled = state.isPermanentDeleteToggleEnabled,
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDeleteConfirmation,
            onTogglePermanentDelete = actions.onTogglePermanentDelete,
            decision = state.deleteDecision,
            isShredChecked = state.isShredChecked,
            onToggleShred = actions.onToggleShred
        )
    }

    if (state.showMixedDeleteExplanation) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedFiles.size,
            isPermanentDeleteChecked = true,
            isPermanentDeleteToggleEnabled = false,
            onConfirm = {},
            onDismiss = actions.onDismissDeleteConfirmation,
            onTogglePermanentDelete = {},
            decision = state.deleteDecision
        )
    }

    if (dialogVisibility.showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { dialogVisibility.showCreateFileDialog = false },
            onConfirm = { fileName ->
                dialogVisibility.showCreateFileDialog = false
                actions.onCreateFile(fileName)
            },
            existingNames = state.displayState.existingNames,
            destinationPath = state.currentPath
        )
    }

    if (dialogVisibility.showCreateFakeFileDialog) {
        CreateFakeFileDialog(
            onDismiss = { dialogVisibility.showCreateFakeFileDialog = false },
            onConfirm = { fileName, size ->
                dialogVisibility.showCreateFakeFileDialog = false
                actions.onCreateFakeFile(fileName, size)
            }
        )
    }

    if (dialogVisibility.showCreateArchiveDialog && state.selectedFiles.isNotEmpty()) {
        val defaultName = remember(state.selectedFiles) {
            state.selectedFiles.singleOrNull()
                ?.let { File(it).nameWithoutExtension }
                ?.ifBlank { "Archive" }
                ?: "Archive"
        }
        CreateArchiveDialog(
            defaultName = defaultName,
            selectedCount = state.selectedFiles.size,
            destinationPath = state.currentPath,
            existingNames = state.displayState.existingNames,
            onDismiss = { dialogVisibility.showCreateArchiveDialog = false },
            onConfirm = { name, format, password ->
                dialogVisibility.showCreateArchiveDialog = false
                actions.onCreateArchiveFromSelection(name, format, password)
            }
        )
    }

    if (dialogVisibility.showExtractArchiveDialog && state.selectedFiles.size == 1) {
        ExtractArchiveDialog(
            archiveName = File(state.selectedFiles.first()).name,
            onDismiss = { dialogVisibility.showExtractArchiveDialog = false },
            onExtractHere = { password ->
                dialogVisibility.showExtractArchiveDialog = false
                actions.onExtractSelectedArchive(password)
            },
            onExtractToFolder = { password ->
                dialogVisibility.showExtractArchiveDialog = false
                actions.onExtractSelectedArchiveToFolder(password)
            }
        )
    }

    if (dialogVisibility.showRenameDialog && state.selectedFiles.size == 1) {
        val selectedPath = state.selectedFiles.first()
        val currentName = selectedPath.substringAfterLast('/')
        RenameDialog(
            currentName = currentName,
            onDismiss = {
                dialogVisibility.showRenameDialog = false
                actions.onClearSelection()
            },
            onConfirm = { newName ->
                actions.onRenameFile(selectedPath, newName)
                dialogVisibility.showRenameDialog = false
            },
            existingNames = state.displayState.existingNames
        )
    }

    if (dialogVisibility.showSortDialog) {
        SortOptionDialog(
            title = stringResource(R.string.sort_folder_title),
            selectedPreferences = currentPresentation,
            showApplyToSubfolders = !state.isCategoryScreen,
            onDismiss = { dialogVisibility.showSortDialog = false },
            onApply = { presentation, applyToSubfolders ->
                actions.onPresentationChange(presentation, applyToSubfolders)
                dialogVisibility.showSortDialog = false
            }
        )
    }

    if (state.showConflictDialog && state.pasteConflicts.isNotEmpty()) {
        PasteConflictDialog(
            conflicts = state.pasteConflicts,
            onResolve = actions.onResolvingConflicts,
            onDismiss = actions.onDismissConflictDialog
        )
    }

    if (state.isPropertiesVisible) {
        PropertiesDialog(
            properties = state.properties,
            isLoading = state.isPropertiesLoading,
            onDismiss = {
                actions.onDismissProperties()
                actions.onClearSelection()
            }
        )
    }

    if (dialogVisibility.showClipboardContents && state.clipboardState != null) {
        ClipboardContentsDialog(
            state = state.clipboardState,
            onRemoveItem = actions.onRemoveFromClipboard,
            onDismiss = { dialogVisibility.showClipboardContents = false }
        )
    }
}
