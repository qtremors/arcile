package dev.qtremors.arcile.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupItemStatus
import dev.qtremors.arcile.feature.onboarding.ui.OnboardingScreen
import dev.qtremors.arcile.core.ui.theme.ThemeState

@Composable
fun OnboardingRoute(
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    hasStoragePermission: Boolean,
    onOpenStoragePermissionSettings: () -> Unit,
    onRestartApp: () -> Unit,
    appContent: @Composable () -> Unit,
    permissionContent: @Composable () -> Unit
) {
    val viewModel = hiltViewModel<OnboardingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val hasNotificationPermission = !notificationPermissionRequired ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.handleNotificationPermissionResult()
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            viewModel.previewBackup(uri)
        }
    }

    LaunchedEffect(
        hasStoragePermission,
        hasNotificationPermission,
        notificationPermissionRequired
    ) {
        viewModel.updatePermissionState(
            hasStoragePermission = hasStoragePermission,
            hasNotificationPermission = hasNotificationPermission,
            notificationPermissionRequired = notificationPermissionRequired
        )
    }

    LaunchedEffect(
        state.preferencesLoaded,
        state.isCompleted,
        hasStoragePermission,
        state.step
    ) {
        if (
            state.preferencesLoaded &&
            !state.isCompleted &&
            hasStoragePermission &&
            state.step == OnboardingStep.WelcomeAndFeatures
        ) {
            viewModel.markExistingUserCompleted()
        }
    }

    val shouldAutoCompleteExistingUser =
        state.preferencesLoaded &&
            !state.isCompleted &&
            hasStoragePermission &&
            state.step == OnboardingStep.WelcomeAndFeatures

    when {
        !state.preferencesLoaded -> Unit
        !state.isCompleted && !shouldAutoCompleteExistingUser -> OnboardingScreen(
            state = state,
            currentThemeState = currentThemeState,
            onThemeChange = onThemeChange,
            onNext = viewModel::next,
            onBack = viewModel::back,
            onStepSelected = viewModel::setStep,
            onOpenStoragePermissionSettings = onOpenStoragePermissionSettings,
            onRequestNotificationPermission = {
                if (notificationPermissionRequired) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.handleNotificationPermissionResult()
                }
            },
            showOlderAndroidWarning = Build.VERSION.SDK_INT < Build.VERSION_CODES.R,
            restoreState = state.backupState.toRestoreState(context),
            onChooseRestoreBackup = {
                restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            },
            onApplyRestoreBackup = {
                pendingRestoreUri?.let(viewModel::restoreBackup)
            },
            onDismissRestoreBackup = {
                pendingRestoreUri = null
                viewModel.dismissBackup()
            },
            onRestartApp = onRestartApp
        )
        hasStoragePermission -> appContent()
        else -> permissionContent()
    }
}

private fun OnboardingBackupState.toRestoreState(context: Context): OnboardingRestoreState =
    when (this) {
        OnboardingBackupState.Idle -> OnboardingRestoreState.Idle
        OnboardingBackupState.Busy -> OnboardingRestoreState.Busy
        is OnboardingBackupState.Failed -> OnboardingRestoreState.Failed(
            message ?: context.getString(R.string.settings_backup_failed_title)
        )
        is OnboardingBackupState.Preview -> OnboardingRestoreState.Preview(
            items = value.items.map { item ->
                OnboardingRestoreItem(
                    label = item.label,
                    status = item.status.label(context)
                )
            }
        )
        is OnboardingBackupState.Restored -> OnboardingRestoreState.Restored(
            items = value.items.map { item ->
                OnboardingRestoreItem(
                    label = item.label,
                    status = item.status.label(context)
                )
            },
            failures = value.failures.map { failure ->
                OnboardingRestoreFailure(failure.label, failure.message)
            }
        )
    }

private fun PreferencesBackupItemStatus.label(context: Context): String =
    context.getString(
        when (this) {
            PreferencesBackupItemStatus.Exported -> R.string.settings_backup_status_exported
            PreferencesBackupItemStatus.WillRestore -> R.string.settings_backup_status_will_restore
            PreferencesBackupItemStatus.WillReset -> R.string.settings_backup_status_will_reset
            PreferencesBackupItemStatus.Restored -> R.string.settings_backup_status_restored
            PreferencesBackupItemStatus.Reset -> R.string.settings_backup_status_reset
        }
    )
