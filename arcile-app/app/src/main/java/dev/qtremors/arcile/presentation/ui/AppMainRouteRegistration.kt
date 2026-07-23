package dev.qtremors.arcile.presentation.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.feature.browser.BrowserDestination
import dev.qtremors.arcile.feature.home.HomeDestination
import dev.qtremors.arcile.navigation.AppRoutes

internal fun NavGraphBuilder.registerMainRoute(
    navController: NavHostController,
    actions: AppNavigationActions,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    composable<AppRoutes.Main> { backStackEntry ->
        MainRoute(
            backStackEntry = backStackEntry,
            mainArgs = backStackEntry.toRoute(),
            hasPreviousRoute = navController.previousBackStackEntry != null,
            onHomeDestination = { destination ->
                handleHomeDestination(destination, navController, actions)
            },
            onBrowserDestination = { destination ->
                when (destination) {
                    BrowserDestination.ExitToPreviousRoute -> navController.popBackStack()
                    is BrowserDestination.OpenFile -> actions.openBrowserFile(
                        destination.path,
                        destination.surroundingFiles
                    )
                    BrowserDestination.ExitToHome -> Unit
                }
            },
            onShareBrowserFiles = actions::shareKnownFiles,
            onFeedback = onFeedback
        )
    }
}

private fun handleHomeDestination(
    destination: HomeDestination,
    navController: NavHostController,
    actions: AppNavigationActions
) {
    when (destination) {
        is HomeDestination.OpenFile -> actions.openPathWithSurroundingImages(
            destination.path,
            destination.context
        )
        is HomeDestination.BrowseCategory -> {
            if (isGalleryCategory(destination.name)) {
                navController.navigate(AppRoutes.ImageGallery(categoryName = destination.name)) {
                    popUpTo<AppRoutes.Main> { saveState = true }
                    launchSingleTop = true
                }
            }
        }
        HomeDestination.Settings -> navController.navigate(AppRoutes.Settings)
        HomeDestination.Tools -> navController.navigate(AppRoutes.Tools) {
            popUpTo<AppRoutes.Main> { saveState = true }
            launchSingleTop = true
        }
        HomeDestination.About -> navController.navigate(AppRoutes.About)
        HomeDestination.Trash -> navController.navigate(AppRoutes.Trash) {
            popUpTo<AppRoutes.Main> { saveState = true }
            launchSingleTop = true
        }
        HomeDestination.RecentFiles -> navController.navigate(AppRoutes.RecentFiles()) {
            popUpTo<AppRoutes.Main> { saveState = true }
            launchSingleTop = true
        }
        HomeDestination.QuickAccess -> navController.navigate(AppRoutes.QuickAccess)
        is HomeDestination.ExternalFolder -> actions.openExternalFolder(destination.uri)
        is HomeDestination.StorageDashboard -> navController.navigate(
            AppRoutes.StorageDashboard(destination.volumeId)
        ) {
            popUpTo<AppRoutes.Main> { saveState = true }
            launchSingleTop = true
        }
        HomeDestination.Cleaner -> navController.navigate(AppRoutes.StorageCleaner) {
            popUpTo<AppRoutes.Main> { saveState = true }
            launchSingleTop = true
        }
        HomeDestination.ActivityLog -> navController.navigate(AppRoutes.ActivityLog) {
            popUpTo<AppRoutes.Main> { saveState = true }
            launchSingleTop = true
        }
        HomeDestination.OnlyFiles -> navController.navigate(AppRoutes.OnlyFiles)
        is HomeDestination.ShareRecentFile -> actions.shareKnownFilesAsync(
            listOf(destination.path),
            destination.context
        )
        HomeDestination.BrowseRoot,
        is HomeDestination.BrowsePath -> Unit
    }
}
