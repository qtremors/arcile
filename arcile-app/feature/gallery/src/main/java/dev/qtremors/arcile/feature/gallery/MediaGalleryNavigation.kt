package dev.qtremors.arcile.feature.gallery

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
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.clipboardStoredFeedback
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import kotlinx.coroutines.launch

sealed interface GalleryDestination {
    data class ViewMedia(
        val path: String,
        val surroundingFiles: List<FileModel>,
        val selectedPaths: Set<String>
    ) : GalleryDestination
}

fun NavGraphBuilder.registerMediaGalleryRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onDestination: (GalleryDestination) -> Unit,
    onShareSelected: suspend (List<FileModel>) -> Boolean,
    onOpenWith: (FileModel) -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    composable<AppRoutes.MediaGallery>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { backStackEntry ->
        val viewModel = hiltViewModel<MediaGalleryViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val viewerReturnPath by backStackEntry.savedStateHandle
            .getStateFlow<String?>(AppRoutes.MEDIA_VIEWER_RETURN_PATH_KEY, null)
            .collectAsStateWithLifecycle()
        val viewerReturnSelectionPaths by backStackEntry.savedStateHandle
            .getStateFlow<ArrayList<String>?>(
                AppRoutes.MEDIA_VIEWER_RETURN_SELECTION_PATHS_KEY,
                null
            )
            .collectAsStateWithLifecycle()
        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(viewModel, onFeedback) {
            viewModel.feedbackEvents.collect(onFeedback)
        }
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
                    AppRoutes.MEDIA_VIEWER_RETURN_SELECTION_PATHS_KEY
                )
            }
        }

        MediaGalleryScreen(
            state = state,
            navigationActions = GalleryNavigationActions(
                navigateBack = onNavigateBack,
                openFile = { path, files, selectedPaths ->
                    onDestination(GalleryDestination.ViewMedia(path, files, selectedPaths))
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
                        val shareFiles = state.files.filter { it.reference in state.selectedFiles }
                        if (onShareSelected(shareFiles)) {
                            viewModel.clearSelection()
                        }
                    }
                },
                openWith = {
                    state.files.singleOrNull {
                        it.reference in state.selectedFiles
                    }?.let(onOpenWith)
                    viewModel.clearSelection()
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
                refresh = { viewModel.loadMedia(forceRefresh = true) },
                searchQueryChange = viewModel::updateSearchQuery,
                searchFiltersChange = viewModel::updateSearchFilters,
                clearSearch = { viewModel.updateSearchQuery("") },
                selectFolder = viewModel::selectFolder,
                clearError = viewModel::clearError,
                feedback = onFeedback
            ),
            presentationActions = GalleryPresentationActions(
                itemsChange = viewModel::updatePresentation,
                foldersChange = viewModel::updateFolderPresentation,
                showFileDetailsChange = viewModel::setShowFileDetails,
                aspectRatioChange = viewModel::updateAspectRatio,
                sectionedChange = viewModel::updateSectioned,
                groupingChange = viewModel::updateGrouping,
                defaultPageChange = viewModel::updateDefaultPage,
                togglePinnedFolder = viewModel::togglePinnedFolder
            ),
            clipboardActions = GalleryClipboardActions(
                copySelected = {
                    viewModel.copySelectedToClipboard().takeIf { it > 0 }?.let { count ->
                        onFeedback(clipboardStoredFeedback(ClipboardOperation.COPY, count))
                    }
                },
                cutSelected = {
                    viewModel.cutSelectedToClipboard().takeIf { it > 0 }?.let { count ->
                        onFeedback(clipboardStoredFeedback(ClipboardOperation.CUT, count))
                    }
                },
                pasteToFolder = viewModel::pasteFromClipboard,
                cancel = viewModel::cancelClipboard,
                remove = viewModel::removeFromClipboard,
                clearActiveOperation = viewModel::clearActiveFileOperation,
                resolveConflicts = viewModel::resolvePasteConflicts,
                dismissConflictDialog = viewModel::dismissPasteConflictDialog
            ),
            fileActions = GalleryFileActions(
                rename = viewModel::renameFile,
                batchRename = viewModel::batchRenameFiles,
                createZipFromSelection = viewModel::createZipFromSelection,
                setFolderCover = viewModel::setFolderCover
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
                    ?.get<LongArray>(AppRoutes.IMAGE_VIEWER_CONTEXT_MODIFIED_KEY),
                contentUris = navController.previousBackStackEntry?.savedStateHandle
                    ?.get<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_CONTENT_URIS_KEY),
                backendIds = navController.previousBackStackEntry?.savedStateHandle
                    ?.get<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_BACKEND_IDS_KEY),
                backendIdentities = navController.previousBackStackEntry?.savedStateHandle
                    ?.get<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_BACKEND_IDENTITIES_KEY)
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
                        AppRoutes.MEDIA_VIEWER_RETURN_SELECTION_PATHS_KEY,
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
    modified: LongArray?,
    contentUris: List<String>?,
    backendIds: List<String>?,
    backendIdentities: List<String>?
): List<FileModel> {
    val hasCompleteMetadata = listOf(names?.size, extensions?.size, mimeTypes?.size,
        sizes?.size, modified?.size).all { it == paths.size }
    if (!hasCompleteMetadata) return paths.distinct().map(::fileModelFromPath)
    val hasNodeMetadata = listOf(contentUris?.size, backendIds?.size, backendIdentities?.size)
        .all { it == paths.size }
    return paths.indices.map { index ->
        val backendId = backendIds?.getOrNull(index).orEmpty()
        val backendIdentity = backendIdentities?.getOrNull(index).orEmpty()
        val contentUri = contentUris?.getOrNull(index)?.ifBlank { null }
        val nodeRef = StorageNodeRef.local(paths[index]).copy(contentUri = contentUri)
        FileModel(
            name = names!![index],
            reference = paths[index],
            size = sizes!![index],
            lastModified = modified!![index],
            extension = extensions!![index],
            mimeType = mimeTypes!![index].ifBlank { null },
            nodeRef = nodeRef
        )
    }.distinctBy(FileModel::reference)
}
