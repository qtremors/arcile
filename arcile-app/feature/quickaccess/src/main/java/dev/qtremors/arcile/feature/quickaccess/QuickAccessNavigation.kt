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
        val viewModel = hiltViewModel<QuickAccessViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        QuickAccessScreen(
            state = state,
            onNavigateBack = onNavigateBack,
            onNavigateToPath = { path ->
                onDestination(QuickAccessDestination.LocalPath(path))
            },
            onNavigateToSaf = { uri ->
                onDestination(QuickAccessDestination.ExternalFolder(uri))
            },
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
