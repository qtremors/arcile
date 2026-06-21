package dev.qtremors.arcile.feature.quickaccess

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.navigation.AppRoutes

fun NavGraphBuilder.quickAccessScreen(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onNavigateToPath: (String) -> Unit,
    onNavigateToSaf: (String) -> Unit
) {
    composable<AppRoutes.QuickAccess>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val viewModel = hiltViewModel<QuickAccessViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        QuickAccessScreen(
            state = state,
            onNavigateBack = onNavigateBack,
            onNavigateToPath = onNavigateToPath,
            onNavigateToSaf = onNavigateToSaf,
            onTogglePin = { viewModel.togglePin(it) },
            onRemoveItem = { viewModel.removeCustomItem(it) },
            onAddCustomFolder = { path, label -> viewModel.addCustomFolder(path, label) },
            onAddSafFolder = { uri, label ->
                if (label == "Files") {
                    viewModel.addFilesAppShortcut(uri)
                } else if (label == "Android/data" || label == "Android/obb") {
                    viewModel.addExternalHandoffFolder(uri, label)
                } else {
                    viewModel.addSafFolder(uri, label)
                }
            },
            onReorderItems = { viewModel.updateItemsOrder(it) }
        )
    }
}
