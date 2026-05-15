package dev.qtremors.arcile.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.BuildConfig
import dev.qtremors.arcile.data.OnboardingPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep {
    WelcomeAndFeatures,
    Privacy,
    Theme,
    SetupPermissions,
    Done
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WelcomeAndFeatures,
    val hasStoragePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val notificationPermissionRequired: Boolean = false,
    val notificationPermissionHandled: Boolean = false,
    val isCompleting: Boolean = false,
    val skipMode: Boolean = false
) {
    val canContinue: Boolean
        get() = step != OnboardingStep.SetupPermissions || hasStoragePermission
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferencesStore: OnboardingPreferencesStore
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

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
            OnboardingStep.WelcomeAndFeatures -> setStep(OnboardingStep.Privacy)
            OnboardingStep.Privacy -> setStep(OnboardingStep.Theme)
            OnboardingStep.Theme -> setStep(OnboardingStep.SetupPermissions)
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
            OnboardingStep.Privacy -> OnboardingStep.WelcomeAndFeatures
            OnboardingStep.Theme -> OnboardingStep.Privacy
            OnboardingStep.SetupPermissions -> if (_state.value.skipMode) OnboardingStep.WelcomeAndFeatures else OnboardingStep.Theme
            OnboardingStep.Done -> OnboardingStep.SetupPermissions
        }
        setStep(previous)
    }

    fun skipToPermissions() {
        _state.update { it.copy(step = OnboardingStep.SetupPermissions, skipMode = true) }
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

    private fun completeOnboarding(markNotificationHandled: Boolean) {
        if (_state.value.isCompleting || !_state.value.hasStoragePermission) return
        _state.update {
            it.copy(
                step = OnboardingStep.Done,
                isCompleting = true,
                notificationPermissionHandled = it.notificationPermissionHandled || markNotificationHandled
            )
        }
        viewModelScope.launch {
            onboardingPreferencesStore.markCompleted(
                completedVersion = BuildConfig.VERSION_CODE,
                notificationPermissionHandled = _state.value.notificationPermissionHandled || markNotificationHandled
            )
            _state.update { it.copy(isCompleting = false) }
        }
    }
}
