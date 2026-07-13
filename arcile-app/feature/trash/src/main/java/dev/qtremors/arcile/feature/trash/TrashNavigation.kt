package dev.qtremors.arcile.feature.trash

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.NativeStorageAuthorizationEffect
import dev.qtremors.arcile.core.storage.domain.FileModel
import kotlinx.coroutines.launch

fun NavGraphBuilder.registerTrashRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onOpenFile: (FileModel, List<FileModel>) -> Unit,
    onOpenFileWith: (FileModel) -> Unit,
    onShareSelected: suspend (List<FileModel>) -> Boolean,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    composable<AppRoutes.Trash>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val viewModel = hiltViewModel<TrashViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val coroutineScope = rememberCoroutineScope()
        val openFileWithContext: (FileModel) -> Unit = { file ->
            val visibleItems = if (state.searchQuery.isNotBlank()) {
                state.searchResults
            } else {
                state.visibleTrashFiles
            }
            onOpenFile(file, visibleItems.map { it.fileModel })
        }
        NativeStorageAuthorizationEffect(
            requirement = state.pendingAuthorization,
            onResult = viewModel::handleAuthorizationResult,
            onUnavailable = viewModel::handleAuthorizationUnavailable
        )
        TrashScreen(
            state = state,
            navigationActions = TrashNavigationActions(onNavigateBack),
            selectionActions = TrashSelectionActions(
                toggle = viewModel::toggleSelection,
                clear = viewModel::clearSelection,
                selectAll = viewModel::selectAll,
                openProperties = viewModel::openPropertiesForSelection,
                dismissProperties = viewModel::dismissProperties
            ),
            fileActions = TrashFileActions(
                open = openFileWithContext,
                openWith = onOpenFileWith,
                shareSelected = {
                    coroutineScope.launch {
                        val files = state.trashFiles
                            .filter { it.id in state.selectedFiles }
                            .map { it.fileModel }
                        if (files.isNotEmpty() && onShareSelected(files)) {
                            viewModel.clearSelection()
                        }
                    }
                }
            ),
            restoreActions = TrashRestoreActions(
                restoreSelected = viewModel::restoreSelectedTrash,
                dismissDestinationPicker = viewModel::dismissDestinationPicker,
                restoreToDestination = viewModel::restoreToDestination,
                undoLastRestore = viewModel::undoLastRestore,
                clearPendingUndo = viewModel::clearPendingRestoreUndo
            ),
            deleteActions = TrashDeleteActions(
                emptyTrash = viewModel::emptyTrash,
                permanentlyDeleteSelected = viewModel::deletePermanentlySelected,
                dismissPermanentDelete = viewModel::dismissPermanentDeleteConfirmation
            ),
            presentationActions = TrashPresentationActions(
                searchQueryChange = viewModel::updateSearchQuery,
                clearSearch = { viewModel.updateSearchQuery("") },
                sortChange = viewModel::updateSortOption,
                filterChange = viewModel::updateFilter,
                refresh = viewModel::loadTrashFiles
            ),
            feedbackActions = TrashFeedbackActions(
                clearError = viewModel::clearError,
                clearSnackbarMessage = viewModel::clearSnackbarMessage,
                feedback = onFeedback
            )
        )
    }
}
