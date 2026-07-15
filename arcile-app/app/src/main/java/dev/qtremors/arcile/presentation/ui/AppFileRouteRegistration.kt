package dev.qtremors.arcile.presentation.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.feature.archive.registerArchiveViewerRoute
import dev.qtremors.arcile.feature.imagegallery.registerImageGalleryRoute
import dev.qtremors.arcile.feature.imagegallery.registerImageViewerRoute
import dev.qtremors.arcile.feature.videoplayer.registerVideoViewerRoute
import dev.qtremors.arcile.feature.recentfiles.registerRecentFilesRoute
import dev.qtremors.arcile.feature.storageusage.StorageDashboardDestination
import dev.qtremors.arcile.feature.storageusage.registerStorageDashboardRoute
import dev.qtremors.arcile.feature.trash.registerTrashRoute
import dev.qtremors.arcile.navigation.AppRoutes

internal fun NavGraphBuilder.registerFileRoutes(
    navController: NavHostController,
    actions: AppNavigationActions,
    transitions: AppNavigationTransitions,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    registerStorageDashboardRoute(
        enterTransition = transitions.detailEnter,
        exitTransition = transitions.detailExit,
        popEnterTransition = transitions.detailPopEnter,
        popExitTransition = transitions.detailPopExit,
        onNavigateBack = { navController.popBackStack() },
        onDestination = { destination ->
            when (destination) {
                is StorageDashboardDestination.Category -> {
                    if (destination.name == FileCategories.Images.name) {
                        navController.navigate(AppRoutes.ImageGallery(destination.volumeId))
                    } else {
                        actions.navigateToBrowser(
                            AppRoutes.Main(
                                initialPage = BROWSER_PAGE,
                                category = destination.name,
                                volumeId = destination.volumeId
                            )
                        )
                    }
                }
                is StorageDashboardDestination.Path -> actions.navigateToBrowser(
                    AppRoutes.Main(initialPage = BROWSER_PAGE, path = destination.path)
                )
                is StorageDashboardDestination.File -> actions.openPath(destination.path)
            }
        }
    )
    composable<AppRoutes.Explorer> { backStackEntry ->
        val explorer = backStackEntry.toRoute<AppRoutes.Explorer>()
        navController.navigate(
            AppRoutes.Main(
                initialPage = BROWSER_PAGE,
                path = explorer.path,
                category = explorer.category,
                volumeId = explorer.volumeId,
                restorePersistentLocation = explorer.restorePersistentLocation
            )
        ) {
            popUpTo<AppRoutes.Main> { inclusive = true }
        }
    }
    registerTrashRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() },
        onOpenFile = actions::openManagedTrashFile,
        onOpenFileWith = actions::openManagedTrashFileWith,
        onShareSelected = actions::shareManagedTrashFiles,
        onFeedback = onFeedback
    )
    registerRecentFilesRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() },
        onOpenFile = actions::openPathWithSurroundingImages,
        onShareSelected = { files ->
            actions.shareKnownFiles(files.map(FileModel::absolutePath), files)
        },
        onDestination = actions.destinationMappers.recentFiles::map,
        onFeedback = onFeedback
    )
    registerImageGalleryRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() },
        onDestination = actions.destinationMappers.gallery::map,
        onShareSelected = { files ->
            actions.shareKnownFiles(files.map(FileModel::absolutePath), files)
        },
        onFeedback = onFeedback
    )
    registerImageViewerRoute(
        navController = navController,
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() },
        onShareFile = actions::shareViewerFile,
        onOpenFileWith = actions::openViewerFileWith
    )
    registerVideoViewerRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() },
        onShare = actions::shareVideo,
        onOpenWith = actions::openVideoWith
    )
    registerArchiveViewerRoute(
        enterTransition = transitions.detailEnter,
        exitTransition = transitions.detailExit,
        popEnterTransition = transitions.detailPopEnter,
        popExitTransition = transitions.detailPopExit,
        onNavigateBack = { navController.popBackStack() },
        onDestination = actions.destinationMappers.archive::map
    )
}
