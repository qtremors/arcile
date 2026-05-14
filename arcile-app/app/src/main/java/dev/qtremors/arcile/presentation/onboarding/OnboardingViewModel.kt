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
    Welcome,
    Features,
    Theme,
    StoragePermission,
    NotificationPermission,
    Done
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val hasStoragePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val notificationPermissionRequired: Boolean = false,
    val notificationPermissionHandled: Boolean = false,
    val isCompleting: Boolean = false,
    val skipMode: Boolean = false
) {
    val canContinue: Boolean
        get() = step != OnboardingStep.StoragePermission || hasStoragePermission
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
            OnboardingStep.Welcome -> moveTo(OnboardingStep.Features)
            OnboardingStep.Features -> moveTo(OnboardingStep.Theme)
            OnboardingStep.Theme -> moveTo(OnboardingStep.StoragePermission)
            OnboardingStep.StoragePermission -> {
                if (_state.value.hasStoragePermission) {
                    moveTo(OnboardingStep.NotificationPermission)
                }
            }
            OnboardingStep.NotificationPermission -> completeOnboarding(markNotificationHandled = true)
            OnboardingStep.Done -> completeOnboarding(markNotificationHandled = false)
        }
    }

    fun back() {
        val previous = when (_state.value.step) {
            OnboardingStep.Welcome -> OnboardingStep.Welcome
            OnboardingStep.Features -> OnboardingStep.Welcome
            OnboardingStep.Theme -> OnboardingStep.Features
            OnboardingStep.StoragePermission -> if (_state.value.skipMode) OnboardingStep.Welcome else OnboardingStep.Theme
            OnboardingStep.NotificationPermission -> OnboardingStep.StoragePermission
            OnboardingStep.Done -> OnboardingStep.NotificationPermission
        }
        moveTo(previous)
    }

    fun skipToPermissions() {
        _state.update { it.copy(step = OnboardingStep.StoragePermission, skipMode = true) }
    }

    fun handleNotificationPermissionResult() {
        completeOnboarding(markNotificationHandled = true)
    }

    fun markExistingUserCompleted() {
        completeOnboarding(markNotificationHandled = _state.value.notificationPermissionHandled)
    }

    private fun moveTo(step: OnboardingStep) {
        _state.update { it.copy(step = step) }
        if (step == OnboardingStep.NotificationPermission && !_state.value.notificationPermissionRequired) {
            completeOnboarding(markNotificationHandled = true)
        }
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
