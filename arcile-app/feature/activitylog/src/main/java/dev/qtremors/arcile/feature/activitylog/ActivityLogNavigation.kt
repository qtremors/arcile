package dev.qtremors.arcile.feature.activitylog

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.feature.activitylog.ui.ActivityLogScreen
import dev.qtremors.arcile.navigation.AppRoutes

fun NavGraphBuilder.registerActivityLogRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit
) {
    composable<AppRoutes.ActivityLog>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val viewModel = hiltViewModel<ActivityLogViewModel>()
        val entries by viewModel.entries.collectAsStateWithLifecycle()
        ActivityLogScreen(
            entries = entries,
            onNavigateBack = onNavigateBack,
            onClearActivity = viewModel::clearActivity
        )
    }
}
