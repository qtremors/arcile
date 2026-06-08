package dev.qtremors.arcile.feature.imagegallery

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
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
import kotlinx.coroutines.launch

fun NavGraphBuilder.imageGalleryScreen(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onShareSelected: suspend (List<String>) -> Boolean,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    composable<AppRoutes.ImageGallery>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val viewModel = hiltViewModel<ImageGalleryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val coroutineScope = rememberCoroutineScope()

        ImageGalleryScreen(
            state = state,
            onNavigateBack = onNavigateBack,
            onOpenFile = onOpenFile,
            onToggleSelection = viewModel::toggleSelection,
            onClearSelection = viewModel::clearSelection,
            onSelectAll = viewModel::selectAll,
            onSelectMultiple = viewModel::selectMultiple,
            onShareSelected = {
                coroutineScope.launch {
                    if (onShareSelected(state.selectedFiles.toList())) {
                        viewModel.clearSelection()
                    }
                }
            },
            onRequestDeleteSelected = viewModel::requestDeleteSelected,
            onConfirmDelete = viewModel::confirmDeleteSelected,
            onTogglePermanentDelete = viewModel::togglePermanentDelete,
            onToggleShred = viewModel::toggleShred,
            onDismissDeleteConfirmation = viewModel::dismissDeleteConfirmation,
            onOpenProperties = viewModel::openPropertiesForSelection,
            onDismissProperties = viewModel::dismissProperties,
            onRefresh = { viewModel.loadImages(forceRefresh = true) },
            onSearchQueryChange = viewModel::updateSearchQuery,
            onClearSearch = { viewModel.updateSearchQuery("") },
            onSelectAlbum = viewModel::selectAlbum,
            onPresentationChange = viewModel::updatePresentation,
            onShowFileDetailsChange = viewModel::setShowFileDetails,
            onClearError = viewModel::clearError,
            onFeedback = onFeedback,
            nativeRequestFlow = viewModel.nativeRequestFlow
        )
    }
}
