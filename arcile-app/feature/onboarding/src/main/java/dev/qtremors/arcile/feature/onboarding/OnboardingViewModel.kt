package dev.qtremors.arcile.feature.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep {
    WelcomeAndFeatures,
    SetupPermissions,
    Done
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WelcomeAndFeatures,
    val hasStoragePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val notificationPermissionRequired: Boolean = false,
    val notificationPermissionHandled: Boolean = false,
    val isCompleting: Boolean = false
) {
    val canContinue: Boolean
        get() = step != OnboardingStep.SetupPermissions || hasStoragePermission
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferencesStore: OnboardingPreferencesStore,
    @ApplicationContext private val context: Context
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

    private fun completeOnboarding(markNotificationHandled: Boolean) {
        if (_state.value.isCompleting || !_state.value.hasStoragePermission) return
        _state.update {
            it.copy(
                step = OnboardingStep.Done,
                isCompleting = true,
                notificationPermissionHandled = it.notificationPermissionHandled || markNotificationHandled
            )
        }

        val versionCode = try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }

        viewModelScope.launch {
            onboardingPreferencesStore.markCompleted(
                completedVersion = versionCode,
                notificationPermissionHandled = _state.value.notificationPermissionHandled || markNotificationHandled
            )
            _state.update { it.copy(isCompleting = false) }
        }
    }
}
