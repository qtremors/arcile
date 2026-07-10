package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.feature.browser.BrowserUiState
import dev.qtremors.arcile.core.ui.PasteConflictDialog
import dev.qtremors.arcile.core.ui.SearchFiltersSheet
import dev.qtremors.arcile.core.ui.SortOptionDialog
import dev.qtremors.arcile.core.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.core.ui.dialogs.CreateFakeFileDialog
import dev.qtremors.arcile.core.ui.dialogs.CreateFileDialog
import dev.qtremors.arcile.core.ui.dialogs.CreateFolderDialog
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import java.io.File

@Composable
internal fun BrowserDialogs(
    state: BrowserUiState,
    currentPresentation: FileListingPreferences,
    dialogVisibility: BrowserDialogVisibility,
    selectionIntents: BrowserSelectionIntents,
    mutationIntents: BrowserMutationIntents,
    searchIntents: BrowserSearchIntents,
    clipboardIntents: BrowserClipboardIntents,
    archiveIntents: BrowserArchiveIntents
) {
    if (state.isSearchFilterMenuVisible) {
        SearchFiltersSheet(
            currentFilters = state.activeSearchFilters,
            onApplyFilters = { searchIntents.onSearchFiltersChange(it) },
            onDismiss = { searchIntents.onToggleSearchFilterMenu(false) },
            showCategoryFilter = !state.isCategoryScreen
        )
    }

    if (dialogVisibility.showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { dialogVisibility.showCreateFolderDialog = false },
            onConfirm = { name ->
                mutationIntents.onCreateFolder(name)
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
            onConfirm = mutationIntents.onConfirmDelete,
            onDismiss = mutationIntents.onDismissDeleteConfirmation,
            onTogglePermanentDelete = mutationIntents.onTogglePermanentDelete,
            decision = state.deleteDecision,
            isShredChecked = state.isShredChecked,
            onToggleShred = mutationIntents.onToggleShred
        )
    }

    if (state.showMixedDeleteExplanation) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedFiles.size,
            isPermanentDeleteChecked = true,
            isPermanentDeleteToggleEnabled = false,
            onConfirm = {},
            onDismiss = mutationIntents.onDismissDeleteConfirmation,
            onTogglePermanentDelete = {},
            decision = state.deleteDecision
        )
    }

    if (dialogVisibility.showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { dialogVisibility.showCreateFileDialog = false },
            onConfirm = { fileName ->
                dialogVisibility.showCreateFileDialog = false
                mutationIntents.onCreateFile(fileName)
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
                mutationIntents.onCreateFakeFile(fileName, size)
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
            onConfirm = { name, format, compressionLevel, password ->
                dialogVisibility.showCreateArchiveDialog = false
                archiveIntents.onCreateArchiveFromSelection(name, format, compressionLevel, password)
            }
        )
    }

    if (dialogVisibility.showExtractArchiveDialog && (state.selectedFiles.size == 1 || state.archiveContext != null)) {
        val archivePath = state.archiveContext?.archivePath ?: state.selectedFiles.first()
        val parentPath = File(archivePath).parent.orEmpty()
        ExtractArchiveDialog(
            archiveName = File(archivePath).name,
            defaultDestinationPath = parentPath,
            onDismiss = { dialogVisibility.showExtractArchiveDialog = false },
            onConfirm = { target, customDestination ->
                dialogVisibility.showExtractArchiveDialog = false
                when {
                    state.archiveContext != null && state.selectedFiles.isNotEmpty() ->
                        archiveIntents.onExtractSelectedArchiveEntries(target, customDestination)
                    state.archiveContext?.entryPrefix != null ->
                        archiveIntents.onExtractCurrentArchiveFolder(target, customDestination)
                    else -> archiveIntents.onExtractArchive(target, customDestination)
                }
            }
        )
    }

    state.archiveContext?.takeIf { it.passwordRequired }?.let { archive ->
        ArchivePasswordPromptDialog(
            archiveName = archive.archiveName,
            onDismiss = archiveIntents.onDismissArchivePassword,
            onConfirm = archiveIntents.onSubmitArchivePassword
        )
    }

    if (dialogVisibility.showRenameDialog && state.selectedFiles.size == 1) {
        val selectedPath = state.selectedFiles.first()
        val currentName = selectedPath.substringAfterLast('/')
        RenameDialog(
            currentName = currentName,
            onDismiss = {
                dialogVisibility.showRenameDialog = false
                selectionIntents.onClearSelection()
            },
            onConfirm = { newName ->
                mutationIntents.onRenameFile(selectedPath, newName)
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
                searchIntents.onPresentationChange(presentation, applyToSubfolders)
                dialogVisibility.showSortDialog = false
            }
        )
    }

    if (state.showConflictDialog && state.pasteConflicts.isNotEmpty()) {
        PasteConflictDialog(
            conflicts = state.pasteConflicts,
            onResolve = clipboardIntents.onResolvingConflicts,
            onDismiss = clipboardIntents.onDismissConflictDialog
        )
    }

    if (state.isPropertiesVisible) {
        PropertiesDialog(
            properties = state.properties,
            isLoading = state.isPropertiesLoading,
            onDismiss = {
                selectionIntents.onDismissProperties()
                selectionIntents.onClearSelection()
            }
        )
    }

    if (dialogVisibility.showClipboardContents && state.clipboardState != null) {
        ClipboardContentsDialog(
            state = state.clipboardState,
            onRemoveItem = clipboardIntents.onRemoveFromClipboard,
            onDismiss = { dialogVisibility.showClipboardContents = false }
        )
    }
}
