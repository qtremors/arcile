package dev.qtremors.arcile.feature.videoplayer

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.media3.common.MediaItem
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.core.ui.StandaloneVideoViewer
import dev.qtremors.arcile.navigation.AppRoutes

fun NavGraphBuilder.registerVideoViewerRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit,
    onShare: (String, Boolean) -> Unit,
    onOpenWith: (String, Boolean) -> Unit
) {
    composable<AppRoutes.VideoViewer>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { entry ->
        val route = entry.toRoute<AppRoutes.VideoViewer>()
        val mediaUri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_FILE)
            .path(route.path)
            .build()
        StandaloneVideoViewer(
            mediaItem = MediaItem.fromUri(mediaUri),
            title = route.path.substringAfterLast('/').substringAfterLast('\\'),
            onNavigateBack = onNavigateBack,
            onShare = { onShare(route.path, route.managedTrash) },
            onOpenWith = { onOpenWith(route.path, route.managedTrash) }
        )
    }
}
