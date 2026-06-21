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
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent

fun NavGraphBuilder.trashScreen(
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
        TrashScreen(
            state = state,
            onNavigateBack = onNavigateBack,
            onToggleSelection = { viewModel.toggleSelection(it) },
            onClearSelection = { viewModel.clearSelection() },
            onRestoreSelected = { viewModel.restoreSelectedTrash() },
            onEmptyTrash = { viewModel.emptyTrash() },
            onClearError = { viewModel.clearError() },
            onDismissDestinationPicker = { viewModel.dismissDestinationPicker() },
            onRestoreToDestination = { ids, path -> viewModel.restoreToDestination(ids, path) },
            onPermanentlyDeleteSelected = { viewModel.deletePermanentlySelected() },
            onDismissPermanentDelete = { viewModel.dismissPermanentDeleteConfirmation() },
            onSelectAll = { viewModel.selectAll() },
            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
            onClearSearch = { viewModel.updateSearchQuery("") },
            onSortChange = { viewModel.updateSortOption(it) },
            onFilterChange = { viewModel.updateFilter(it) },
            onOpenProperties = { viewModel.openPropertiesForSelection() },
            onDismissProperties = { viewModel.dismissProperties() },
            onClearSnackbarMessage = { viewModel.clearSnackbarMessage() },
            onUndoLastRestore = { viewModel.undoLastRestore() },
            onClearPendingRestoreUndo = { viewModel.clearPendingRestoreUndo() },
            onRefresh = { viewModel.loadTrashFiles() },
            onFeedback = onFeedback,
            nativeRequestFlow = viewModel.nativeRequestFlow
        )
    }
}
