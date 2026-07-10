package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import kotlinx.coroutines.launch

sealed interface GalleryDestination {
    data class ViewImage(val path: String) : GalleryDestination
}

fun NavGraphBuilder.registerImageGalleryRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onDestination: (GalleryDestination) -> Unit,
    onShareSelected: suspend (List<FileModel>) -> Boolean,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    composable<AppRoutes.ImageGallery>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { backStackEntry ->
        val viewModel = hiltViewModel<ImageGalleryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val viewerReturnPath by backStackEntry.savedStateHandle
            .getStateFlow<String?>(VIEWER_RETURN_PATH_KEY, null)
            .collectAsStateWithLifecycle()
        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(viewerReturnPath) {
            viewerReturnPath?.let { path ->
                viewModel.setViewerReturnPath(path)
                backStackEntry.savedStateHandle.remove<String>(VIEWER_RETURN_PATH_KEY)
            }
        }

        ImageGalleryScreen(
            state = state,
            navigationActions = GalleryNavigationActions(
                navigateBack = onNavigateBack,
                openFile = { path -> onDestination(GalleryDestination.ViewImage(path)) }
            ),
            selectionActions = GallerySelectionActions(
                toggle = viewModel::toggleSelection,
                clear = viewModel::clearSelection,
                selectAll = viewModel::selectAll,
                invert = viewModel::invertSelection,
                selectMultiple = viewModel::selectMultiple,
                share = {
                    coroutineScope.launch {
                        val shareFiles = state.files.filter { it.absolutePath in state.selectedFiles }
                        if (onShareSelected(shareFiles)) {
                            viewModel.clearSelection()
                        }
                    }
                },
                openProperties = viewModel::openPropertiesForSelection,
                dismissProperties = viewModel::dismissProperties
            ),
            deleteActions = GalleryDeleteActions(
                request = viewModel::requestDeleteSelected,
                confirm = viewModel::confirmDeleteSelected,
                togglePermanent = viewModel::togglePermanentDelete,
                toggleShred = viewModel::toggleShred,
                dismiss = viewModel::dismissDeleteConfirmation
            ),
            contentActions = GalleryContentActions(
                refresh = { viewModel.loadImages(forceRefresh = true) },
                searchQueryChange = viewModel::updateSearchQuery,
                clearSearch = { viewModel.updateSearchQuery("") },
                selectAlbum = viewModel::selectAlbum,
                clearError = viewModel::clearError,
                feedback = onFeedback
            ),
            presentationActions = GalleryPresentationActions(
                photosChange = viewModel::updatePresentation,
                albumsChange = viewModel::updateAlbumPresentation,
                showFileDetailsChange = viewModel::setShowFileDetails,
                aspectRatioChange = viewModel::updateAspectRatio,
                sectionedChange = viewModel::updateSectioned,
                groupingChange = viewModel::updateGrouping,
                defaultTabChange = viewModel::updateDefaultTab,
                togglePinnedAlbum = viewModel::togglePinnedAlbum
            ),
            clipboardActions = GalleryClipboardActions(
                copySelected = viewModel::copySelectedToClipboard,
                cutSelected = viewModel::cutSelectedToClipboard,
                pasteToAlbum = viewModel::pasteFromClipboard,
                cancel = viewModel::cancelClipboard,
                remove = viewModel::removeFromClipboard,
                clearActiveOperation = viewModel::clearActiveFileOperation,
                resolveConflicts = viewModel::resolvePasteConflicts,
                dismissConflictDialog = viewModel::dismissPasteConflictDialog
            ),
            fileActions = GalleryFileActions(
                rename = viewModel::renameFile,
                createZipFromSelection = viewModel::createZipFromSelection,
                setAlbumCover = viewModel::setAlbumCover
            ),
            nativeRequestFlow = viewModel.nativeRequestFlow
        )
    }
}
fun NavGraphBuilder.registerImageViewerRoute(
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
        val contextPaths = remember(backStackEntry) {
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_PATHS_KEY)
                ?.toList()
                .orEmpty()
        }

        val viewModel = hiltViewModel<ImageViewerViewModel>()
        val nativeRequestLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {}
        LaunchedEffect(route.initialPath, contextPaths) {
            viewModel.initialize(route.initialPath, contextPaths)
        }
        LaunchedEffect(viewModel.nativeRequestFlow) {
            viewModel.nativeRequestFlow.collect { sender ->
                nativeRequestLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
        val navigateBack = {
            viewModel.state.value.viewerCurrentPath?.let { path ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(VIEWER_RETURN_PATH_KEY, path)
            }
            if (route.returnToBrowserPage) {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("showBrowserPage", true)
            }
            onNavigateBack()
        }

        ImageViewerScreen(
            initialPath = route.initialPath,
            viewModel = viewModel,
            contextPaths = contextPaths,
            onNavigateBack = navigateBack,
            onShareFile = onShareFile,
            onOpenWith = onOpenFileWith
        )
    }
}

private const val VIEWER_RETURN_PATH_KEY = "image_viewer.return_path"
