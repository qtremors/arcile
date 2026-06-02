package dev.qtremors.arcile.feature.archive

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

fun NavGraphBuilder.archiveViewerScreen(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit
) {
    composable<AppRoutes.ArchiveViewer>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val viewModel = hiltViewModel<ArchiveViewerViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        ArchiveViewerScreen(
            state = state,
            onNavigateBack = onNavigateBack,
            onNavigateUpInArchive = { viewModel.navigateBack() },
            onOpenFolder = { viewModel.openFolder(it) },
            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
            onExtractAll = { password -> viewModel.extractAll(password) },
            onExtractCurrentFolder = { password -> viewModel.extractCurrentFolder(password) },
            onSubmitPassword = { viewModel.submitPassword(it) },
            onSelectNameEncoding = { viewModel.selectNameEncoding(it) },
            onSetConflictResolution = { path, resolution -> viewModel.setConflictResolution(path, resolution) },
            onApplyConflictResolutionToAll = { viewModel.applyConflictResolutionToAll(it) },
            onConfirmConflictResolutions = { viewModel.confirmConflictResolutions() },
            onDismissConflicts = { viewModel.dismissConflicts() },
            onClearError = { viewModel.clearError() },
            onCancelExtraction = { viewModel.cancelExtraction() },
            onClearOperationStatusMessage = { viewModel.clearOperationStatusMessage() },
            onClearActiveOperation = { viewModel.clearActiveOperation() },
            onToggleItemSelection = { viewModel.toggleItemSelection(it) },
            onClearSelection = { viewModel.clearSelection() },
            onExtractSelected = { password -> viewModel.extractSelected(password) },
            onSelectAll = { viewModel.selectAllVisible() }
        )
    }
}
