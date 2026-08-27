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
import androidx.navigation.toRoute
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.viewer.ViewerActionHost
import dev.qtremors.arcile.core.ui.viewer.ViewerSourceScope
import dev.qtremors.arcile.core.ui.viewer.rememberViewerActionController
import java.io.File

sealed interface ArchiveDestination {
    data class OpenInBrowser(val archivePath: String) : ArchiveDestination
}

fun NavGraphBuilder.registerArchiveViewerRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onDestination: (ArchiveDestination) -> Unit
) {
    composable<AppRoutes.ArchiveViewer>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val route = it.toRoute<AppRoutes.ArchiveViewer>()
        androidx.compose.runtime.LaunchedEffect(route.archivePath) {
            onDestination(ArchiveDestination.OpenInBrowser(route.archivePath))
        }
        val viewModel = hiltViewModel<ArchiveViewerViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val archiveFileModel = androidx.compose.runtime.remember(route.archivePath) {
            val file = File(route.archivePath)
            FileModel(
                name = file.name,
                absolutePath = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                extension = file.extension,
                isDirectory = false
            )
        }
        val viewerActionController = rememberViewerActionController(
            currentFile = archiveFileModel,
            sourceScope = ViewerSourceScope.Normal,
            onFileRenamed = { _, renamed ->
                onDestination(ArchiveDestination.OpenInBrowser(renamed.absolutePath))
                onNavigateBack()
            },
            onFileDeleted = { onNavigateBack() }
        )
        ArchiveViewerScreen(
            state = state,
            navigationActions = ArchiveNavigationActions(
                navigateBack = onNavigateBack,
                navigateUpInArchive = viewModel::navigateBack,
                openFolder = viewModel::openFolder,
                searchQueryChange = viewModel::updateSearchQuery
            ),
            extractionActions = ArchiveExtractionActions(
                extractAll = viewModel::extractAll,
                extractCurrentFolder = viewModel::extractCurrentFolder,
                submitPassword = viewModel::submitPassword,
                selectNameEncoding = viewModel::selectNameEncoding,
                cancelExtraction = viewModel::cancelExtraction,
                clearError = viewModel::clearError,
                clearOperationStatusMessage = viewModel::clearOperationStatusMessage,
                clearActiveOperation = viewModel::clearActiveOperation
            ),
            conflictActions = ArchiveConflictActions(
                setResolution = viewModel::setConflictResolution,
                applyResolutionToAll = viewModel::applyConflictResolutionToAll,
                confirmResolutions = viewModel::confirmConflictResolutions,
                dismissConflicts = viewModel::dismissConflicts
            ),
            selectionActions = ArchiveSelectionActions(
                toggleItem = viewModel::toggleItemSelection,
                clear = viewModel::clearSelection,
                extractSelected = viewModel::extractSelected,
                selectAll = viewModel::selectAllVisible
            ),
            archiveFileModel = archiveFileModel,
            viewerActions = viewerActionController.state.allowedActions,
            onViewerAction = viewerActionController::onAction
        )
        ViewerActionHost(viewerActionController)
    }
}
