package dev.qtremors.arcile.feature.storagecleaner

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.feature.storagecleaner.ui.StorageCleanerGroupScreen
import dev.qtremors.arcile.feature.storagecleaner.ui.StorageCleanerScreen
import dev.qtremors.arcile.navigation.AppRoutes

sealed interface StorageCleanerDestination {
    data class OpenFile(val path: String) : StorageCleanerDestination
    data class ContainingFolder(
        val path: String,
        val focusPath: String
    ) : StorageCleanerDestination
}

fun NavGraphBuilder.registerStorageCleanerRoute(
    navController: NavHostController,
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onDestination: (StorageCleanerDestination) -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    navigation<AppRoutes.StorageCleaner>(startDestination = AppRoutes.StorageCleanerOverview) {
        composable<AppRoutes.StorageCleanerOverview>(
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition
        ) { entry ->
            val viewModel = sharedStorageCleanerViewModel(navController, entry)
            val state by viewModel.state.collectAsStateWithLifecycle()
            StorageCleanerScreen(
                state = state,
                onNavigateBack = onNavigateBack,
                onRefresh = { viewModel.scan(pullToRefresh = true) },
                onOpenGroup = { type ->
                    navController.navigate(AppRoutes.StorageCleanerGroup(type.name)) {
                        launchSingleTop = true
                    }
                },
                onClearThumbnailCache = viewModel::clearThumbnailCache,
                onUndoClean = viewModel::undoClean,
                onClearMessages = viewModel::clearMessages,
                onUnignorePath = viewModel::unignorePath,
                onFeedback = onFeedback
            )
        }

        composable<AppRoutes.StorageCleanerGroup>(
            enterTransition = enterTransition,
            exitTransition = exitTransition,
            popEnterTransition = popEnterTransition,
            popExitTransition = popExitTransition
        ) { entry ->
            val route = entry.toRoute<AppRoutes.StorageCleanerGroup>()
            val type = CleanerGroupType.entries.firstOrNull { it.name == route.type }
            if (type == null) {
                LaunchedEffect(route.type) { navController.popBackStack() }
                return@composable
            }
            val viewModel = sharedStorageCleanerViewModel(navController, entry)
            val state by viewModel.state.collectAsStateWithLifecycle()
            DisposableEffect(entry) {
                onDispose { viewModel.refreshThumbnailCache() }
            }
            LaunchedEffect(type) { viewModel.scanGroup(type) }
            StorageCleanerGroupScreen(
                state = state,
                type = type,
                onNavigateBack = { navController.popBackStack() },
                onRefresh = { viewModel.refreshGroup(type, pullToRefresh = true) },
                onCleanFiles = viewModel::clean,
                onUndoClean = viewModel::undoClean,
                onClearMessages = viewModel::clearMessages,
                onOpenFile = { path ->
                    onDestination(StorageCleanerDestination.OpenFile(path))
                },
                onOpenContainingFolder = { focusPath ->
                    val parentPath = storageParentPath(focusPath).orEmpty()
                    if (parentPath.isNotBlank()) {
                        onDestination(
                            StorageCleanerDestination.ContainingFolder(
                                path = parentPath,
                                focusPath = focusPath
                            )
                        )
                    }
                },
                onUpdateSectionRule = viewModel::updateSectionRule,
                onResetSectionRule = viewModel::resetSectionRule,
                onIgnorePath = viewModel::ignorePath,
                onFeedback = onFeedback
            )
        }
    }
}

@Composable
private fun sharedStorageCleanerViewModel(
    navController: NavHostController,
    entry: NavBackStackEntry
): StorageCleanerViewModel {
    val parentEntry = remember(entry) {
        navController.getBackStackEntry<AppRoutes.StorageCleaner>()
    }
    return hiltViewModel(parentEntry)
}
