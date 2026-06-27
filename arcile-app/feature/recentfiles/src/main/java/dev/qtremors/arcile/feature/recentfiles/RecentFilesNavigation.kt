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
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
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
            onNavigateBack = onNavigateBack,
            onOpenFile = { path -> onOpenFile(path, openFiles) },
            onToggleSelection = { viewModel.toggleSelection(it) },
            onClearSelection = { viewModel.clearSelection() },
            onRequestDeleteSelected = { viewModel.requestDeleteSelected() },
            onConfirmDelete = { viewModel.confirmDeleteSelected() },
            onTogglePermanentDelete = { viewModel.togglePermanentDelete() },
            onToggleShred = { viewModel.toggleShred() },
            onDismissDeleteConfirmation = { viewModel.dismissDeleteConfirmation() },
            onShareSelected = {
                coroutineScope.launch {
                    val shareFiles = openFiles.filter { it.absolutePath in state.selectedFiles }
                    if (onShareSelected(shareFiles)) {
                        viewModel.clearSelection()
                    }
                }
            },
            onSelectAll = { viewModel.selectAll() },
            onRefresh = { viewModel.loadRecentFiles(pullToRefresh = true) },
            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
            onClearSearch = { viewModel.updateSearchQuery("") },
            onSearchFiltersChange = { viewModel.updateSearchFilters(it) },
            onPresentationChange = { viewModel.updatePresentation(it) },
            onSelectMultiple = { viewModel.selectMultiple(it) },
            onLoadMore = { viewModel.loadMore() },
            onClearError = { viewModel.clearError() },
            onOpenProperties = { viewModel.openPropertiesForSelection() },
            onDismissProperties = { viewModel.dismissProperties() },
            onOpenContainingFolder = { path ->
                onDestination(RecentFilesDestination.ContainingFolder(path))
            },
            onFeedback = onFeedback,
            nativeRequestFlow = viewModel.nativeRequestFlow
        )
    }
}
