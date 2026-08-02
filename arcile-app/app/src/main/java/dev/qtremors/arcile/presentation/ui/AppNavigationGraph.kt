package dev.qtremors.arcile.presentation.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.storage.domain.AppStartPage

@Composable
fun AppNavigationGraph(
    navController: NavHostController,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenFileWith: (String) -> Unit,
    fileOpenBehaviors: Map<String, FileOpenBehavior>,
    appStartPage: AppStartPage,
    onAppStartPageChange: (AppStartPage) -> Unit,
    onRestartApp: () -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    val actions = rememberAppNavigationActions(
        navController = navController,
        onOpenFile = onOpenFile,
        onOpenFileWith = onOpenFileWith,
        fileOpenBehaviors = fileOpenBehaviors,
        onFeedback = onFeedback
    )
    val transitions = appNavigationTransitions(LocalReducedMotionEnabled.current)

    AppPluginPromptDialog(
        prompt = actions.pluginPrompt,
        onDismiss = actions::dismissPluginPrompt
    )

    AppApkInstallerDialog(
        target = actions.apkInstallTarget,
        onDismiss = actions::dismissApkInstaller
    )

    NavHost(
        navController = navController,
        startDestination = AppRoutes.Main(),
        enterTransition = transitions.rootEnter,
        exitTransition = transitions.rootExit,
        popEnterTransition = transitions.rootPopEnter,
        popExitTransition = transitions.rootPopExit
    ) {
        registerMainRoute(
            navController,
            actions,
            appStartPage,
            onAppStartPageChange,
            onFeedback
        )
        registerFileRoutes(navController, actions, transitions, onFeedback)
        registerUtilityRoutes(
            navController = navController,
            actions = actions,
            transitions = transitions,
            currentThemeState = currentThemeState,
            onThemeChange = onThemeChange,
            onRestartApp = onRestartApp,
            onFeedback = onFeedback
        )
    }
}
