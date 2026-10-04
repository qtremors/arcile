package dev.qtremors.arcile.feature.settings

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.theme.UiPreferences

sealed interface SettingsDestination {
    data object StorageManagement : SettingsDestination
    data object Plugins : SettingsDestination
    data object About : SettingsDestination
}

fun NavGraphBuilder.registerSettingsRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    currentUiPreferences: UiPreferences,
    onThemeChange: (UiPreferences) -> Unit,
    onNavigateBack: () -> Unit,
    onDestination: (SettingsDestination) -> Unit,
    onRestartApp: () -> Unit
) {
    composable<AppRoutes.Settings>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        SettingsRoute(
            currentUiPreferences = currentUiPreferences,
            onThemeChange = onThemeChange,
            onNavigateBack = onNavigateBack,
            onDestination = onDestination,
            onRestartApp = onRestartApp
        )
    }
}
