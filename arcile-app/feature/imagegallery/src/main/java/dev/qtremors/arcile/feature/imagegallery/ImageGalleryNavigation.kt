package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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
            onInvertSelection = viewModel::invertSelection,
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
            onCopySelected = viewModel::copySelectedToClipboard,
            onCutSelected = viewModel::cutSelectedToClipboard,
            onRenameFile = viewModel::renameFile,
            onCreateZipFromSelection = viewModel::createZipFromSelection,
            onSetAlbumCover = viewModel::setAlbumCover,
            onAspectRatioChange = viewModel::updateAspectRatio,
            onSectionedChange = viewModel::updateSectioned,
            onGroupingChange = viewModel::updateGrouping,
            onAlbumPresentationChange = viewModel::updateAlbumPresentation,
            onAlbumAspectRatioChange = viewModel::updateAlbumAspectRatio,
            onFeedback = onFeedback,
            nativeRequestFlow = viewModel.nativeRequestFlow
        )
    }
}

fun NavGraphBuilder.imageViewerScreen(
    navController: NavHostController,
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onShareFile: (String) -> Unit,
    onOpenFileWith: (String) -> Unit
) {
    composable<AppRoutes.ImageViewer>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<AppRoutes.ImageViewer>()

        // Try to obtain the active ImageGalleryViewModel from parent backstack entry
        val parentEntry = remember(backStackEntry) {
            runCatching { navController.getBackStackEntry<AppRoutes.ImageGallery>() }.getOrNull()
        }
        val viewModel = if (parentEntry != null) {
            hiltViewModel<ImageGalleryViewModel>(parentEntry)
        } else {
            hiltViewModel<ImageGalleryViewModel>()
        }

        ImageViewerScreen(
            initialPath = route.initialPath,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            onShareFile = onShareFile,
            onOpenWith = onOpenFileWith
        )
    }
}
