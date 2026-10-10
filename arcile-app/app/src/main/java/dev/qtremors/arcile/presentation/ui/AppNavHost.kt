package dev.qtremors.arcile.presentation.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.qtremors.arcile.navigation.AppRoutes

@Composable
internal fun AppNavHost(
    navController: NavHostController,
    transitions: AppNavigationTransitions,
    builder: NavGraphBuilder.() -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.Main(),
        enterTransition = transitions.rootEnter,
        exitTransition = transitions.rootExit,
        popEnterTransition = transitions.rootPopEnter,
        popExitTransition = transitions.rootPopExit,
        // Navigation 2.10 no longer uses destination pop transitions during the gesture.
        // Match each page's existing motion so committing back does not switch animations.
        predictivePopEnterTransition = { transitions.popEnterFor(targetState.destination).invoke(this) },
        predictivePopExitTransition = { transitions.popExitFor(initialState.destination).invoke(this) },
        builder = builder
    )
}

private fun AppNavigationTransitions.popEnterFor(destination: NavDestination): AppEnterTransition = when {
    destination.usesUtilityTransitions() -> utilityPopEnter
    destination.hasRoute<AppRoutes.StorageDashboard>() || destination.hasRoute<AppRoutes.ArchiveViewer>() -> detailPopEnter
    else -> rootPopEnter
}

private fun AppNavigationTransitions.popExitFor(destination: NavDestination): AppExitTransition = when {
    destination.usesUtilityTransitions() -> utilityPopExit
    destination.hasRoute<AppRoutes.StorageDashboard>() || destination.hasRoute<AppRoutes.ArchiveViewer>() -> detailPopExit
    else -> rootPopExit
}

private fun NavDestination.usesUtilityTransitions(): Boolean = utilityRoutes.any { hasRoute(it) }

private val utilityRoutes = listOf(
    AppRoutes.Tools::class,
    AppRoutes.ActivityLog::class,
    AppRoutes.OnlyFiles::class,
    AppRoutes.Settings::class,
    AppRoutes.Plugins::class,
    AppRoutes.Trash::class,
    AppRoutes.RecentFiles::class,
    AppRoutes.MediaGallery::class,
    AppRoutes.ImageViewer::class,
    AppRoutes.VideoViewer::class,
    AppRoutes.AudioLibrary::class,
    AppRoutes.StorageCleanerOverview::class,
    AppRoutes.StorageCleanerGroup::class,
    AppRoutes.StorageManagement::class,
    AppRoutes.QuickAccess::class,
    AppRoutes.About::class,
    AppRoutes.Licenses::class
)
