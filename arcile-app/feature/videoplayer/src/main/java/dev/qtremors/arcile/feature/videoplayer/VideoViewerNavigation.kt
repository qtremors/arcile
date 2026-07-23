package dev.qtremors.arcile.feature.videoplayer

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
                        absolutePath = path,
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
            LaunchedEffect(session, initialPath, contextFiles) {
                viewModel.initialize(
                    initialPath = initialPath,
                    contextFiles = contextFiles,
                    selectedPaths = session.initialSelectedPaths.toList(),
                    discoverSiblings = session.files == null
                )
            }

            val readOnly = session.managedTrash || session.securityScopeId != null
            val navigateBack = {
                viewModel.state.value.viewerCurrentPath?.let { path ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(AppRoutes.MEDIA_VIEWER_RETURN_PATH_KEY, path)
                }
                if (session.initialSelectedPaths.isNotEmpty()) {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(
                            AppRoutes.IMAGE_VIEWER_RETURN_SELECTION_PATHS_KEY,
                            ArrayList(viewModel.state.value.selectedFiles)
                        )
                }
                GlobalVideoPlaybackSessions.remove(route.sessionToken)
                onNavigateBack()
            }

            VideoViewerScreen(
                session = session,
                viewModel = viewModel,
                selectionModeEnabled = session.initialSelectedPaths.isNotEmpty() && !readOnly,
                readOnly = readOnly,
                onNavigateBack = navigateBack,
                onShareFile = { file -> onShareFile(file, session.managedTrash) },
                onOpenWith = { file -> onOpenFileWith(file, session.managedTrash) }
            )
        }
    }
}
