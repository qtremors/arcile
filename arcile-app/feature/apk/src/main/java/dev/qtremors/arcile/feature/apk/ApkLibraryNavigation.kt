package dev.qtremors.arcile.feature.apk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.category.CategoryLibraryFileActionCallbacks
import kotlinx.coroutines.launch

fun NavGraphBuilder.registerApkLibraryRoute(
    onNavigateBack: () -> Unit,
    onInstall: (FileModel) -> Unit,
    onOpenWith: (FileModel) -> Unit,
    onShare: suspend (List<FileModel>) -> Boolean,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    composable<AppRoutes.ApkLibrary> {
        val viewModel = hiltViewModel<ApkLibraryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        ApkLibraryScreen(
            state = state,
            onNavigateBack = onNavigateBack,
            onQueryChange = viewModel::updateQuery,
            onSearchFiltersChange = viewModel::updateSearchFilters,
            onTabChange = viewModel::selectTab,
            onPresentationChange = viewModel::updatePresentation,
            onDefaultPageChange = viewModel::updateDefaultPage,
            onGroupingChange = viewModel::updateGrouping,
            onShowFileDetailsChange = viewModel::updateShowFileDetails,
            onRefresh = viewModel::load,
            onClearFolderFilter = viewModel::clearFolderFilter,
            onToggleSelection = viewModel::toggleSelection,
            onSelectPaths = viewModel::selectPaths,
            onClearSelection = viewModel::clearSelection,
            onSelectAll = viewModel::selectAll,
            onInvertSelection = viewModel::invertSelection,
            onInstall = onInstall,
            onOpenFolder = viewModel::openFolder,
            onShareSelection = {
                val selected = state.allFiles.filter { it.absolutePath in state.selectedPaths }
                scope.launch {
                    if (onShare(selected)) viewModel.clearSelection()
                }
            },
            onOpenSelectionWith = {
                state.allFiles.singleOrNull { it.absolutePath in state.selectedPaths }?.let(onOpenWith)
            },
            onClearError = viewModel::clearError,
            onFeedback = onFeedback,
            fileActions = CategoryLibraryFileActionCallbacks(
                state = state.fileActions,
                onCopy = viewModel::copySelection,
                onCut = viewModel::cutSelection,
                onDelete = viewModel::requestDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onDismissDelete = viewModel::dismissDelete,
                onTogglePermanentDelete = viewModel::togglePermanentDelete,
                onToggleShred = viewModel::toggleShred,
                onRename = viewModel::renameSelected,
                onCreateZip = viewModel::createZip,
                onOpenProperties = viewModel::openProperties,
                onDismissProperties = viewModel::dismissProperties,
                onPasteToFolder = viewModel::pasteToFolder,
                onCancelClipboard = viewModel::cancelClipboard,
                onRemoveFromClipboard = viewModel::removeFromClipboard,
                onClearActiveOperation = viewModel::clearActiveOperation,
                onClearError = viewModel::clearActionError,
                onResolvePasteConflicts = viewModel::resolvePasteConflicts,
                onDismissPasteConflictDialog = viewModel::dismissPasteConflictDialog
            )
        )
    }
}
