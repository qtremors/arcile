package dev.qtremors.arcile.feature.trash

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.NativeStorageAuthorizationEffect

fun NavGraphBuilder.registerTrashRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
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
