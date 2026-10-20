package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultId

internal data class OnlyFilesEntry(val vaultId: VaultId, val directoryId: DirectoryId, val focusNodeId: String?)

class VaultPluginViewer(val name: String, val launch: (VaultExternalGrant) -> Boolean)

internal fun AppRoutes.OnlyFiles.toVaultEntry(): OnlyFilesEntry? {
    val vault = vaultId ?: return null
    return runCatching {
        OnlyFilesEntry(
            VaultId.of(vault),
            directoryId?.let(DirectoryId::of) ?: DirectoryId.Root,
            focusNodeId?.let { NodeId.of(it).value }
        )
    }.getOrNull()
}

fun NavGraphBuilder.registerOnlyFilesRoute(
    getFileOpenBehaviors: () -> Map<String, FileOpenBehavior>,
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onPlayVideo: (VideoPlaybackSession) -> Unit,
    findPlugin: (VaultNodeMetadata) -> VaultPluginViewer?,
    packageInstaller: @Composable (VaultExternalGrant, () -> Unit) -> Unit
) {
    composable<AppRoutes.OnlyFiles>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { backStack ->
        val route = backStack.toRoute<AppRoutes.OnlyFiles>()
        OnlyFilesRoute(
            getFileOpenBehaviors = getFileOpenBehaviors,
            entry = route.toVaultEntry(),
            importRequestId = route.importRequestId,
            onNavigateBack = onNavigateBack,
            onPlayVideo = onPlayVideo,
            findPlugin = findPlugin,
            packageInstaller = packageInstaller
        )
    }
}
