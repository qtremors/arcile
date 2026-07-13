package dev.qtremors.arcile.feature.quickaccess

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.navigation.AppRoutes

sealed interface QuickAccessDestination {
    data class LocalPath(val path: String) : QuickAccessDestination
    data class ExternalFolder(val uri: String) : QuickAccessDestination
}

fun NavGraphBuilder.registerQuickAccessRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onDestination: (QuickAccessDestination) -> Unit
) {
    composable<AppRoutes.QuickAccess>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        QuickAccessRoute(
            onNavigateBack = onNavigateBack,
            onDestination = onDestination
        )
    }
}
