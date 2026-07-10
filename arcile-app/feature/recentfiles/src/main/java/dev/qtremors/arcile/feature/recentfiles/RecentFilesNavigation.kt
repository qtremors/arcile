package dev.qtremors.arcile.feature.recentfiles

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
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.feature.recentfiles.ui.RecentFilesScreen
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import kotlinx.coroutines.launch

sealed interface RecentFilesDestination {
    data class ContainingFolder(val path: String) : RecentFilesDestination
}

fun NavGraphBuilder.registerRecentFilesRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onOpenFile: (String, List<FileModel>) -> Unit,
    onShareSelected: suspend (List<FileModel>) -> Boolean,
    onDestination: (RecentFilesDestination) -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    composable<AppRoutes.RecentFiles>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val viewModel = hiltViewModel<RecentFilesViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val coroutineScope = rememberCoroutineScope()
        val openFiles = if (state.searchQuery.isNotBlank()) state.searchResults else state.displayedRecentFiles
        RecentFilesScreen(
            state = state,
            navigationActions = dev.qtremors.arcile.feature.recentfiles.ui.RecentNavigationActions(
                navigateBack = onNavigateBack,
                openFile = { path -> onOpenFile(path, openFiles) },
                openContainingFolder = { path ->
                    onDestination(RecentFilesDestination.ContainingFolder(path))
                }
            ),
            selectionActions = dev.qtremors.arcile.feature.recentfiles.ui.RecentSelectionActions(
                toggle = viewModel::toggleSelection,
                clear = viewModel::clearSelection,
                share = {
                    coroutineScope.launch {
                        val shareFiles = openFiles.filter { it.absolutePath in state.selectedFiles }
                        if (onShareSelected(shareFiles)) {
                            viewModel.clearSelection()
                        }
                    }
                },
                selectAll = viewModel::selectAll,
                selectMultiple = viewModel::selectMultiple,
                openProperties = viewModel::openPropertiesForSelection,
                dismissProperties = viewModel::dismissProperties
            ),
            deleteActions = dev.qtremors.arcile.feature.recentfiles.ui.RecentDeleteActions(
                request = viewModel::requestDeleteSelected,
                confirm = viewModel::confirmDeleteSelected,
                togglePermanent = viewModel::togglePermanentDelete,
                toggleShred = viewModel::toggleShred,
                dismissConfirmation = viewModel::dismissDeleteConfirmation
            ),
            searchActions = dev.qtremors.arcile.feature.recentfiles.ui.RecentSearchActions(
                queryChange = viewModel::updateSearchQuery,
                clear = { viewModel.updateSearchQuery("") },
                filtersChange = viewModel::updateSearchFilters,
                presentationChange = viewModel::updatePresentation,
                loadMore = viewModel::loadMore
            ),
            contentActions = dev.qtremors.arcile.feature.recentfiles.ui.RecentContentActions(
                refresh = { viewModel.loadRecentFiles(pullToRefresh = true) },
                clearError = viewModel::clearError,
                feedback = onFeedback
            ),
            nativeRequestFlow = viewModel.nativeRequestFlow
        )
    }
}
