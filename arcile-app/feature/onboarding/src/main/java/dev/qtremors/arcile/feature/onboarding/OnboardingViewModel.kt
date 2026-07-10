package dev.qtremors.arcile.feature.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.AppVersionCodeProvider
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal enum class OnboardingStep {
    WelcomeAndFeatures,
    SetupPermissions,
    Done
}

internal data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WelcomeAndFeatures,
    val hasStoragePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val notificationPermissionRequired: Boolean = false,
    val notificationPermissionHandled: Boolean = false,
    val isCompleting: Boolean = false,
    val preferencesLoaded: Boolean = false,
    val isCompleted: Boolean = false,
    val backupState: OnboardingBackupState = OnboardingBackupState.Idle
) {
    val canContinue: Boolean
        get() = step != OnboardingStep.SetupPermissions || hasStoragePermission
}

@HiltViewModel
internal class OnboardingViewModel @Inject constructor(
    private val onboardingPreferencesStore: OnboardingPreferencesStore,
    private val backupGateway: PreferencesBackupGateway,
    private val appVersionCodeProvider: AppVersionCodeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            onboardingPreferencesStore.preferencesFlow.collect { preferences ->
                _state.update {
                    it.copy(
                        preferencesLoaded = true,
                        isCompleted = preferences.isCompleted
                    )
                }
            }
        }
    }

    fun updatePermissionState(
        hasStoragePermission: Boolean,
        hasNotificationPermission: Boolean,
        notificationPermissionRequired: Boolean
    ) {
        _state.update {
            it.copy(
                hasStoragePermission = hasStoragePermission,
                hasNotificationPermission = hasNotificationPermission,
                notificationPermissionRequired = notificationPermissionRequired,
                notificationPermissionHandled = it.notificationPermissionHandled ||
                    !notificationPermissionRequired ||
                    hasNotificationPermission
            )
        }
    }

    fun next() {
        when (_state.value.step) {
            OnboardingStep.WelcomeAndFeatures -> setStep(OnboardingStep.SetupPermissions)
            OnboardingStep.SetupPermissions -> {
                if (_state.value.hasStoragePermission) {
                    completeOnboarding(markNotificationHandled = true)
                }
            }
            OnboardingStep.Done -> completeOnboarding(markNotificationHandled = false)
        }
    }

    fun back() {
        val previous = when (_state.value.step) {
            OnboardingStep.WelcomeAndFeatures -> OnboardingStep.WelcomeAndFeatures
            OnboardingStep.SetupPermissions -> OnboardingStep.WelcomeAndFeatures
            OnboardingStep.Done -> OnboardingStep.SetupPermissions
        }
        setStep(previous)
    }

    fun handleNotificationPermissionResult() {
        _state.update { it.copy(notificationPermissionHandled = true) }
    }

    fun markExistingUserCompleted() {
        completeOnboarding(markNotificationHandled = _state.value.notificationPermissionHandled)
    }

    fun setStep(step: OnboardingStep) {
        _state.update { it.copy(step = step) }
    }

    fun previewBackup(uri: Uri) {
        if (_state.value.backupState == OnboardingBackupState.Busy) return
        _state.update { it.copy(backupState = OnboardingBackupState.Busy) }
        viewModelScope.launch {
            backupGateway.preview(uri).fold(
                onSuccess = { preview ->
                    _state.update {
                        it.copy(backupState = OnboardingBackupState.Preview(preview))
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(backupState = OnboardingBackupState.Failed(error.message))
                    }
                }
            )
        }
    }

    fun restoreBackup(uri: Uri) {
        if (_state.value.backupState == OnboardingBackupState.Busy) return
        _state.update { it.copy(backupState = OnboardingBackupState.Busy) }
        viewModelScope.launch {
            backupGateway.restoreFrom(uri).fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(backupState = OnboardingBackupState.Restored(result))
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(backupState = OnboardingBackupState.Failed(error.message))
                    }
                }
            )
        }
    }

    fun dismissBackup() {
        _state.update { it.copy(backupState = OnboardingBackupState.Idle) }
    }

    private fun completeOnboarding(markNotificationHandled: Boolean) {
        if (_state.value.isCompleting || !_state.value.hasStoragePermission) return
        _state.update {
            it.copy(
                step = OnboardingStep.Done,
                isCompleting = true,
                notificationPermissionHandled = it.notificationPermissionHandled || markNotificationHandled
            )
        }

        val versionCode = appVersionCodeProvider.currentVersionCode()

        viewModelScope.launch {
            onboardingPreferencesStore.markCompleted(
                completedVersion = versionCode,
                notificationPermissionHandled = _state.value.notificationPermissionHandled || markNotificationHandled
            )
            _state.update { it.copy(isCompleting = false) }
        }
    }
}
