package dev.qtremors.arcile.feature.onboarding

import dev.qtremors.arcile.core.ui.backup.PreferencesBackupOperationResult
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupPreview

internal sealed interface OnboardingBackupState {
    data object Idle : OnboardingBackupState
    data object Busy : OnboardingBackupState
    data class Preview(val value: PreferencesBackupPreview) : OnboardingBackupState
    data class Restored(val value: PreferencesBackupOperationResult) : OnboardingBackupState
    data class Failed(val message: String?) : OnboardingBackupState
}

internal sealed interface OnboardingRestoreState {
    data object Idle : OnboardingRestoreState
    data object Busy : OnboardingRestoreState
    data class Preview(val items: List<OnboardingRestoreItem>) : OnboardingRestoreState
    data class Restored(
        val items: List<OnboardingRestoreItem>,
        val failures: List<OnboardingRestoreFailure>
    ) : OnboardingRestoreState
    data class Failed(val message: String) : OnboardingRestoreState
}

internal data class OnboardingRestoreItem(
    val label: String,
    val status: String
)

internal data class OnboardingRestoreFailure(
    val label: String,
    val message: String
)
