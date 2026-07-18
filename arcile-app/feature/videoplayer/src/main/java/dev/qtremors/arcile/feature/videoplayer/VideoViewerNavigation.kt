package dev.qtremors.arcile.feature.videoplayer

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.video.GlobalVideoPlaybackSessions

fun NavGraphBuilder.registerVideoViewerRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    onNavigateBack: () -> Unit
) {
    composable<AppRoutes.VideoViewer>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { entry ->
        val route = entry.toRoute<AppRoutes.VideoViewer>()
        val session = remember(route.sessionToken) { GlobalVideoPlaybackSessions.resolve(route.sessionToken) }
        if (session == null) {
            LaunchedEffect(route.sessionToken) { onNavigateBack() }
        } else {
            GlobalVideoViewer(
                session = session,
                onNavigateBack = {
                    GlobalVideoPlaybackSessions.remove(route.sessionToken)
                    onNavigateBack()
                }
            )
        }
    }
}
