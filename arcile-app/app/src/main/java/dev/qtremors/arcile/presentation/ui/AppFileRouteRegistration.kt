package dev.qtremors.arcile.presentation.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.feature.apk.registerApkLibraryRoute
import dev.qtremors.arcile.feature.archive.registerArchiveViewerRoute
import dev.qtremors.arcile.feature.audio.registerAudioLibraryRoute
import dev.qtremors.arcile.feature.browser.BrowserDestination
import dev.qtremors.arcile.feature.browser.BrowserEntry
import dev.qtremors.arcile.feature.browser.BrowserEntryRequest
import dev.qtremors.arcile.feature.browser.BrowserRoute
import dev.qtremors.arcile.feature.gallery.registerMediaGalleryRoute
import dev.qtremors.arcile.feature.gallery.registerImageViewerRoute
import dev.qtremors.arcile.feature.documents.registerDocumentLibraryRoute
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
                    when {
                        isGalleryCategory(destination.name) -> {
                            navController.navigate(
                                AppRoutes.MediaGallery(
                                    volumeId = destination.volumeId,
                                    categoryId = FileCategories.find(destination.name)?.id?.value
                                        ?: FileCategories.Images.id.value
                                )
                            )
                        }
                        isAudioCategory(destination.name) -> {
                            navController.navigate(
                                AppRoutes.AudioLibrary(volumeId = destination.volumeId)
                            )
                        }
                        isDocumentCategory(destination.name) -> {
                            navController.navigate(
                                AppRoutes.DocumentLibrary(volumeId = destination.volumeId)
                            )
                        }
                        isApkCategory(destination.name) -> {
                            navController.navigate(
                                AppRoutes.ApkLibrary(volumeId = destination.volumeId)
                            )
                        }
                        else -> {
                            navController.navigate(
                                AppRoutes.Category(
                                    id = FileCategories.find(destination.name)?.id?.value
                                        ?: destination.name,
                                    volumeId = destination.volumeId
                                )
                            )
                        }
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
    composable<AppRoutes.Category> { backStackEntry ->
        val category = backStackEntry.toRoute<AppRoutes.Category>()
        BrowserRoute(
            entryRequest = BrowserEntryRequest(
                id = 0L,
                entry = BrowserEntry.Category(category.id, category.volumeId)
            ),
            isVisible = true,
            hasPreviousRoute = true,
            onStatusChange = {},
            onDestination = { destination ->
                when (destination) {
                    BrowserDestination.ExitToHome,
                    BrowserDestination.ExitToPreviousRoute -> navController.popBackStack()
                    is BrowserDestination.OpenFile -> actions.openPathWithContext(
                        destination.path,
                        destination.surroundingFiles
                    )
                    is BrowserDestination.OpenFileWith -> actions.openFileWith(destination.path)
                    is BrowserDestination.OpenFileAs -> actions.openFileAs(destination.path, destination.type)
                }
            },
            onShareSelected = actions::shareKnownFiles,
            onFeedback = onFeedback
        )
    }
    registerDocumentLibraryRoute(
        onNavigateBack = { navController.popBackStack() },
        onOpenFile = { file, context -> actions.openPathWithContext(file.reference, context) },
        onOpenWith = { actions.openFileWith(it.reference) },
        onShare = { files -> actions.shareKnownFiles(files.map { it.reference }, files) },
        onFeedback = onFeedback
    )
    registerApkLibraryRoute(
        onNavigateBack = { navController.popBackStack() },
        onInstall = { actions.openPath(it.reference) },
        onOpenWith = { actions.openFileWith(it.reference) },
        onShare = { files -> actions.shareKnownFiles(files.map { it.reference }, files) },
        onFeedback = onFeedback
    )
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
        onOpenFile = actions::openPathWithContext,
        onOpenFileWith = actions::openFileWith,
        onShareSelected = { files ->
            actions.shareKnownFiles(files.map(FileModel::reference), files)
        },
        onDestination = actions.destinationMappers.recentFiles::map,
        onFeedback = onFeedback
    )
    registerMediaGalleryRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() },
        onDestination = actions.destinationMappers.gallery::map,
        onShareSelected = { files ->
            actions.shareKnownFiles(files.map(FileModel::reference), files)
        },
        onOpenWith = { actions.openFileWith(it.reference) },
        onFeedback = onFeedback
    )
    registerAudioLibraryRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = {
            if (!navController.popBackStack()) {
                navController.navigate(AppRoutes.Main()) {
                    launchSingleTop = true
                }
            }
        },
        onShareSelected = { tracks ->
            actions.shareKnownFiles(
                tracks.map { it.file.reference },
                tracks.map { it.file }
            )
        },
        onOpenPlayer = actions::openAudioPlayer,
        onEditSelected = { tracks ->
            actions.openAudioEditor(tracks.map { it.file.reference })
        },
        onOpenWith = { track -> actions.openFileWith(track.file.reference) },
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
        navController = navController,
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() },
        onShareFile = actions::shareViewerFile,
        onOpenFileWith = actions::openViewerFileWith
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
