package dev.qtremors.arcile.feature.videoplayer

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.navigation.AppRoutes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.video.GlobalVideoPlaybackSessions

fun NavGraphBuilder.registerVideoViewerRoute(
    navController: NavHostController,
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onShareFile: (FileModel, Boolean) -> Unit,
    onOpenFileWith: (FileModel, Boolean) -> Unit
) {
    composable<AppRoutes.VideoViewer>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { entry ->
        val route = entry.toRoute<AppRoutes.VideoViewer>()
        val session = remember(route.sessionToken) { GlobalVideoPlaybackSessions.resolve(route.sessionToken) }
        if (session == null) {
            LaunchedEffect(route.sessionToken) { onNavigateBack() }
        } else {
            val contextFiles = remember(session) {
                session.files ?: session.items.map { item ->
                    val path = videoPlaybackReference(item)
                    FileModel(
                        name = item.title,
                        reference = path,
                        size = 0L,
                        lastModified = 0L,
                        isDirectory = false,
                        extension = path.substringAfterLast('.', "").lowercase(),
                        mimeType = item.mediaItem.localConfiguration?.mimeType
                    )
                }
            }
            val initialPath = remember(session) {
                videoPlaybackInitialPath(session)
            }
            val viewModel = hiltViewModel<VideoViewerViewModel>()
            val sourceUpdate = session.sourceUpdates?.collectAsStateWithLifecycle()?.value
            val activeSession = if (sourceUpdate == null || sourceUpdate.items.isEmpty()) session else session.copy(
                items = sourceUpdate.items,
                files = sourceUpdate.files,
                startIndex = sourceUpdate.files.indexOfFirst { it.reference == initialPath }.coerceAtLeast(0),
                sourceOverlayVisible = sourceUpdate.overlayVisible
            )
            LaunchedEffect(session, initialPath, contextFiles) {
                viewModel.initialize(
                    initialPath = initialPath,
                    contextFiles = contextFiles,
                    selectedPaths = session.initialSelectedPaths.toList(),
                    discoverSiblings = session.files == null,
                    persistFileReferences = session.securityScopeId == null
                )
            }
            LaunchedEffect(sourceUpdate?.files) {
                sourceUpdate?.let { viewModel.updateSourceFiles(it.files) }
            }

            var isNavigatingBack by remember { mutableStateOf(false) }
            val readOnly = session.managedTrash || session.securityScopeId != null
            val navigateBack = {
                if (!isNavigatingBack) {
                    isNavigatingBack = true
                    viewModel.state.value.viewerCurrentPath?.takeIf { session.securityScopeId == null }?.let { path ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(AppRoutes.MEDIA_VIEWER_RETURN_PATH_KEY, path)
                    }
                    if (session.securityScopeId == null && session.initialSelectedPaths.isNotEmpty()) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(
                                AppRoutes.MEDIA_VIEWER_RETURN_SELECTION_PATHS_KEY,
                                ArrayList(viewModel.state.value.selectedFiles)
                            )
                    }
                    GlobalVideoPlaybackSessions.remove(route.sessionToken)
                    onNavigateBack()
                }
            }

            val viewer: @Composable () -> Unit = {
                VideoViewerScreen(
                    session = activeSession,
                    viewModel = viewModel,
                    selectionModeEnabled = session.initialSelectedPaths.isNotEmpty() && !readOnly,
                    readOnly = readOnly,
                    onNavigateBack = navigateBack,
                    onShareFile = { file -> onShareFile(file, session.managedTrash) },
                    onOpenWith = { file -> onOpenFileWith(file, session.managedTrash) }
                )
            }
            val sourceHost = session.sourceHost
            if (sourceHost == null) viewer() else sourceHost(navigateBack, viewer)
        }
    }
}
