package dev.qtremors.arcile.presentation.ui

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.feature.activitylog.registerActivityLogRoute
import dev.qtremors.arcile.feature.plugins.registerPluginsRoute
import dev.qtremors.arcile.feature.quickaccess.registerQuickAccessRoute
import dev.qtremors.arcile.feature.settings.SettingsDestination
import dev.qtremors.arcile.feature.settings.registerSettingsRoute
import dev.qtremors.arcile.feature.storagecleaner.registerStorageCleanerRoute
import dev.qtremors.arcile.feature.storageusage.registerStorageManagementRoute
import dev.qtremors.arcile.navigation.AppRoutes

internal fun NavGraphBuilder.registerUtilityRoutes(
    navController: NavHostController,
    actions: AppNavigationActions,
    transitions: AppNavigationTransitions,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onRestartApp: () -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    composable<AppRoutes.Tools>(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit
    ) {
        ToolsRoute(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToCleaner = { navController.navigate(AppRoutes.StorageCleaner) },
            onNavigateToTrash = {
                navController.navigate(AppRoutes.Trash) {
                    popUpTo<AppRoutes.Main> { saveState = true }
                    launchSingleTop = true
                }
            },
            onNavigateToActivity = { navController.navigate(AppRoutes.ActivityLog) }
        )
    }
    registerActivityLogRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() }
    )
    registerStorageCleanerRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() },
        onDestination = actions.destinationMappers.storageCleaner::map,
        onFeedback = onFeedback
    )
    registerSettingsRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        currentThemeState = currentThemeState,
        onThemeChange = onThemeChange,
        onNavigateBack = { navController.popBackStack() },
        onDestination = { destination ->
            when (destination) {
                SettingsDestination.StorageManagement -> {
                    navController.navigate(AppRoutes.StorageManagement)
                }
                SettingsDestination.Plugins -> navController.navigate(AppRoutes.Plugins)
                SettingsDestination.About -> navController.navigate(AppRoutes.About)
            }
        },
        onRestartApp = onRestartApp
    )
    registerPluginsRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() }
    )
    registerStorageManagementRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() }
    )
    composable<AppRoutes.About>(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit
    ) {
        AboutScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToLicenses = { navController.navigate(AppRoutes.Licenses) }
        )
    }
    composable<AppRoutes.Licenses>(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit
    ) {
        LicensesScreen(onNavigateBack = { navController.popBackStack() })
    }
    registerQuickAccessRoute(
        enterTransition = transitions.utilityEnter,
        exitTransition = transitions.utilityExit,
        popEnterTransition = transitions.utilityPopEnter,
        popExitTransition = transitions.utilityPopExit,
        onNavigateBack = { navController.popBackStack() },
        onDestination = actions.destinationMappers.quickAccess::map
    )
}
