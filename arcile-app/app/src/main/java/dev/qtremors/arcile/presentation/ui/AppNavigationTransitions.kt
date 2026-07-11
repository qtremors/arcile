package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry

internal typealias AppEnterTransition =
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
internal typealias AppExitTransition =
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

internal data class AppNavigationTransitions(
    val rootEnter: AppEnterTransition,
    val rootExit: AppExitTransition,
    val rootPopEnter: AppEnterTransition,
    val rootPopExit: AppExitTransition,
    val detailEnter: AppEnterTransition,
    val detailExit: AppExitTransition,
    val detailPopEnter: AppEnterTransition,
    val detailPopExit: AppExitTransition,
    val utilityEnter: AppEnterTransition,
    val utilityExit: AppExitTransition,
    val utilityPopEnter: AppEnterTransition,
    val utilityPopExit: AppExitTransition
)

private fun <T> appSpring(): SpringSpec<T> = spring(
    dampingRatio = 0.75f,
    stiffness = Spring.StiffnessMediumLow
)

internal fun appNavigationTransitions(reducedMotion: Boolean) = AppNavigationTransitions(
    rootEnter = {
        if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(appSpring()) + scaleIn(initialScale = 0.94f, animationSpec = appSpring())
    },
    rootExit = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(
            targetOffsetX = { -it / 3 },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeOut(appSpring()) + scaleOut(targetScale = 1.04f, animationSpec = appSpring())
    },
    rootPopEnter = {
        if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(
            initialOffsetX = { -it / 3 },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(appSpring()) + scaleIn(initialScale = 1.04f, animationSpec = appSpring())
    },
    rootPopExit = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeOut(appSpring()) + scaleOut(targetScale = 0.94f, animationSpec = appSpring())
    },
    detailEnter = {
        if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(appSpring()) + scaleIn(initialScale = 0.94f, animationSpec = appSpring())
    },
    detailExit = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(
            targetOffsetX = { -it / 3 },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeOut(appSpring()) + scaleOut(targetScale = 1.04f, animationSpec = appSpring())
    },
    detailPopEnter = {
        if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(
            initialOffsetX = { -it / 3 },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(appSpring()) + scaleIn(initialScale = 1.04f, animationSpec = appSpring())
    },
    detailPopExit = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeOut(appSpring()) + scaleOut(targetScale = 0.94f, animationSpec = appSpring())
    },
    utilityEnter = {
        if (reducedMotion) fadeIn(tween(0)) else slideInVertically(
            initialOffsetY = { it / 8 },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(appSpring())
    },
    utilityExit = {
        if (reducedMotion) fadeOut(tween(0)) else fadeOut(appSpring())
    },
    utilityPopEnter = {
        if (reducedMotion) fadeIn(tween(0)) else fadeIn(appSpring())
    },
    utilityPopExit = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutVertically(
            targetOffsetY = { it / 8 },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeOut(appSpring())
    }
)
