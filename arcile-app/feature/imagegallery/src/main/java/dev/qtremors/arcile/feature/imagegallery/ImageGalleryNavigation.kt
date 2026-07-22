package dev.qtremors.arcile.feature.imagegallery

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
    data class ViewImage(
        val path: String,
        val surroundingFiles: List<FileModel>,
        val selectedPaths: Set<String>
    ) : GalleryDestination
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
            .getStateFlow<String?>(AppRoutes.MEDIA_VIEWER_RETURN_PATH_KEY, null)
            .collectAsStateWithLifecycle()
        val viewerReturnSelectionPaths by backStackEntry.savedStateHandle
            .getStateFlow<ArrayList<String>?>(
                AppRoutes.IMAGE_VIEWER_RETURN_SELECTION_PATHS_KEY,
                null
            )
            .collectAsStateWithLifecycle()
        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(viewerReturnPath) {
            viewerReturnPath?.let { path ->
                viewModel.setViewerReturnPath(path)
                backStackEntry.savedStateHandle.remove<String>(AppRoutes.MEDIA_VIEWER_RETURN_PATH_KEY)
            }
        }
        LaunchedEffect(viewerReturnSelectionPaths) {
            viewerReturnSelectionPaths?.let { paths ->
                viewModel.replaceSelection(paths)
                backStackEntry.savedStateHandle.remove<ArrayList<String>>(
                    AppRoutes.IMAGE_VIEWER_RETURN_SELECTION_PATHS_KEY
                )
            }
        }

        ImageGalleryScreen(
            state = state,
            navigationActions = GalleryNavigationActions(
                navigateBack = onNavigateBack,
                openFile = { path, files, selectedPaths ->
                    onDestination(GalleryDestination.ViewImage(path, files, selectedPaths))
                }
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
            )
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
    onShareFile: (FileModel, Boolean) -> Unit,
    onOpenFileWith: (FileModel, Boolean) -> Unit
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
        val initialSelectionPaths = remember(backStackEntry) {
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_SELECTION_PATHS_KEY)
                ?.toList()
                .orEmpty()
        }
        val contextFiles = remember(backStackEntry, contextPaths) {
            viewerContextFiles(
                paths = contextPaths,
                names = navController.previousBackStackEntry?.savedStateHandle
                    ?.get<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_NAMES_KEY),
                extensions = navController.previousBackStackEntry?.savedStateHandle
                    ?.get<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_EXTENSIONS_KEY),
                mimeTypes = navController.previousBackStackEntry?.savedStateHandle
                    ?.get<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_MIME_TYPES_KEY),
                sizes = navController.previousBackStackEntry?.savedStateHandle
                    ?.get<LongArray>(AppRoutes.IMAGE_VIEWER_CONTEXT_SIZES_KEY),
                modified = navController.previousBackStackEntry?.savedStateHandle
                    ?.get<LongArray>(AppRoutes.IMAGE_VIEWER_CONTEXT_MODIFIED_KEY)
            )
        }

        val viewModel = hiltViewModel<ImageViewerViewModel>()
        LaunchedEffect(route.initialPath, contextFiles, initialSelectionPaths, route.managedTrash) {
            viewModel.initialize(
                route.initialPath,
                contextFiles,
                initialSelectionPaths,
                discoverSiblings = !route.managedTrash
            )
        }
        val navigateBack = {
            viewModel.state.value.viewerCurrentPath?.let { path ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(AppRoutes.MEDIA_VIEWER_RETURN_PATH_KEY, path)
            }
            if (initialSelectionPaths.isNotEmpty()) {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(
                        AppRoutes.IMAGE_VIEWER_RETURN_SELECTION_PATHS_KEY,
                        ArrayList(viewModel.state.value.selectedFiles)
                    )
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
            contextFiles = contextFiles,
            selectionModeEnabled = initialSelectionPaths.isNotEmpty(),
            readOnly = route.managedTrash,
            onNavigateBack = navigateBack,
            onShareFile = { file -> onShareFile(file, route.managedTrash) },
            onOpenWith = { file -> onOpenFileWith(file, route.managedTrash) }
        )
    }
}

private fun viewerContextFiles(
    paths: List<String>,
    names: List<String>?,
    extensions: List<String>?,
    mimeTypes: List<String>?,
    sizes: LongArray?,
    modified: LongArray?
): List<FileModel> {
    val hasCompleteMetadata = listOf(names?.size, extensions?.size, mimeTypes?.size,
        sizes?.size, modified?.size).all { it == paths.size }
    if (!hasCompleteMetadata) return paths.distinct().map(::fileModelFromPath)
    return paths.indices.map { index ->
        FileModel(
            name = names!![index],
            absolutePath = paths[index],
            size = sizes!![index],
            lastModified = modified!![index],
            extension = extensions!![index],
            mimeType = mimeTypes!![index].ifBlank { null }
        )
    }.distinctBy(FileModel::absolutePath)
}
