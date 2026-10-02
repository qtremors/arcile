package dev.qtremors.arcile.presentation.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.theme.LocalReducedMotionEnabled
import dev.qtremors.arcile.core.ui.theme.UiPreferences
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.ActivityLogPage

@Composable
fun AppNavigationGraph(
    navController: NavHostController,
    currentUiPreferences: UiPreferences,
    onThemeChange: (UiPreferences) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenFileWith: (String) -> Unit,
    onRecordFileOpened: (String) -> Unit,
    onRecordPageVisited: (ActivityLogPage, String?) -> Unit,
    fileOpenBehaviors: Map<String, FileOpenBehavior>,
    appStartPage: AppStartPage,
    onAppStartPageChange: (AppStartPage) -> Unit,
    onRestartApp: () -> Unit,
    enableStartupUpdateCheck: Boolean = false,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    val actions = rememberAppNavigationActions(
        navController = navController,
        onOpenFile = onOpenFile,
        onOpenFileWith = onOpenFileWith,
        onRecordFileOpened = onRecordFileOpened,
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

    ArcileUpdatePromptCoordinator(
        enabled = enableStartupUpdateCheck,
        onInstallUpdate = actions::openPath,
        onFeedback = onFeedback
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
            currentUiPreferences.landscapeDualPaneEnabled,
            onRecordPageVisited,
            onFeedback
        )
        registerFileRoutes(navController, actions, transitions, onFeedback)
        registerUtilityRoutes(
            navController = navController,
            actions = actions,
            transitions = transitions,
            currentUiPreferences = currentUiPreferences,
            onThemeChange = onThemeChange,
            onRestartApp = onRestartApp,
            onFeedback = onFeedback
        )
    }
}
