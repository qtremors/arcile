package dev.qtremors.arcile.feature.storageusage

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.feature.storageusage.ui.StorageDashboardScreen
import dev.qtremors.arcile.feature.storageusage.ui.StorageManagementScreen
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef

sealed interface StorageDashboardDestination {
    data class Category(val name: String, val volumeId: String?) : StorageDashboardDestination
    data class Path(val path: String) : StorageDashboardDestination
    data class File(val path: String) : StorageDashboardDestination
}

fun NavGraphBuilder.registerStorageDashboardRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onDestination: (StorageDashboardDestination) -> Unit
) {
    composable<AppRoutes.StorageDashboard>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<AppRoutes.StorageDashboard>()
        val selectedVolumeId = route.volumeId?.takeIf(String::isNotBlank)
        val explicitRoot = route.toStorageNodeRef()
        val overviewViewModel = hiltViewModel<StorageOverviewViewModel>()
        val usageViewModel = hiltViewModel<StorageUsageViewModel>()
        val overviewState by overviewViewModel.state.collectAsStateWithLifecycle()
        val usageState by usageViewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(selectedVolumeId) {
            overviewViewModel.load(selectedVolumeId)
        }
        LaunchedEffect(explicitRoot?.canonicalIdentity?.value, explicitRoot?.backendId) {
            explicitRoot?.let(usageViewModel::loadExplicitRoot)
        }
        StorageDashboardScreen(
            state = overviewState,
            usageState = usageState,
            selectedVolumeId = selectedVolumeId,
            explicitScope = explicitRoot != null,
            onNavigateBack = onNavigateBack,
            onCategoryClick = { category, volumeId ->
                onDestination(StorageDashboardDestination.Category(category, volumeId))
            },
            onOpenPath = { onDestination(StorageDashboardDestination.Path(it)) },
            onOpenFile = { onDestination(StorageDashboardDestination.File(it)) },
            onLoadUsage = { volumeId ->
                explicitRoot?.let(usageViewModel::loadExplicitRoot)
                    ?: usageViewModel.load(volumeId)
            },
            onSelectUsageNode = usageViewModel::selectNode,
            onDrillIntoUsageNode = usageViewModel::drillInto,
            onUsageBreadcrumbClick = usageViewModel::navigateToBreadcrumb,
            onResetUsageOverview = usageViewModel::resetToOverview,
            onRefreshUsage = { usageViewModel.refresh() }
        )

    }
}

internal fun AppRoutes.StorageDashboard.toStorageNodeRef(): StorageNodeRef? {
    val path = scopePath?.takeIf(String::isNotBlank) ?: return null
    return when (scopeBackendId) {
        null,
        StorageNodeRef.LOCAL_BACKEND_ID -> StorageNodeRef.local(path)
        StorageNodeRef.ROOT_BACKEND_ID,
        StorageNodeRef.SHIZUKU_BACKEND_ID -> StorageNodeRef.privileged(
            backendId = requireNotNull(scopeBackendId),
            displayPath = path,
            remoteCanonicalIdentity = scopeBackendIdentity?.takeIf(String::isNotBlank)
                ?: return null
        )
        else -> null
    }
}

fun NavGraphBuilder.registerStorageManagementRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit
) {
    composable<AppRoutes.StorageManagement>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val viewModel = hiltViewModel<StorageOverviewViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.load(null) }
        StorageManagementScreen(
            state = state,
            onNavigateBack = onNavigateBack,
            onSetVolumeClassification = viewModel::setVolumeClassification,
            onResetVolumeClassification = viewModel::resetVolumeClassification
        )
    }
}
