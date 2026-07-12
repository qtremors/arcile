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
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.storage.domain.storageParentPath

sealed interface StorageCleanerDestination {
    data class OpenFile(val path: String) : StorageCleanerDestination
    data class ContainingFolder(
        val path: String,
        val focusPath: String
    ) : StorageCleanerDestination
}

fun NavGraphBuilder.registerStorageCleanerRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onDestination: (StorageCleanerDestination) -> Unit,
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
            onClearThumbnailCache = { viewModel.clearThumbnailCache() },
            onCleanFiles = { paths, acknowledgedHighRisk -> viewModel.clean(paths, acknowledgedHighRisk) },
            onUndoClean = { viewModel.undoClean(it) },
            onClearMessages = { viewModel.clearMessages() },
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
            onUpdateSectionRule = { type, rule -> viewModel.updateSectionRule(type, rule) },
            onResetSectionRule = { type -> viewModel.resetSectionRule(type) },
            onIgnorePath = { path -> viewModel.ignorePath(path) },
            onUnignorePath = { path -> viewModel.unignorePath(path) },
            onFeedback = onFeedback
        )
    }
}
