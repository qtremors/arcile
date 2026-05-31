package dev.qtremors.arcile.feature.storagecleaner

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.feature.storagecleaner.ui.StorageCleanerScreen
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent

fun NavGraphBuilder.storageCleanerScreen(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    composable<AppRoutes.StorageCleaner>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val viewModel = hiltViewModel<StorageCleanerViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        StorageCleanerScreen(
            state = state,
            onNavigateBack = onNavigateBack,
            onRefresh = { viewModel.scan() },
            onCleanFiles = { viewModel.clean(it) },
            onUndoClean = { viewModel.undoClean(it) },
            onClearMessages = { viewModel.clearMessages() },
            onFeedback = onFeedback
        )
    }
}
