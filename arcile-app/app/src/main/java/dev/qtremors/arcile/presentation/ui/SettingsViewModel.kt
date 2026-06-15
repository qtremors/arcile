package dev.qtremors.arcile.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.net.Uri
import dev.qtremors.arcile.backup.PreferencesBackupOperationResult
import dev.qtremors.arcile.backup.PreferencesBackupPreview
import dev.qtremors.arcile.backup.PreferencesBackupManager
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val browserPreferencesStore: BrowserPreferencesStore,
    private val onboardingPreferencesStore: OnboardingPreferencesStore,
    private val preferencesBackupManager: PreferencesBackupManager
) : ViewModel() {
    val browserPreferences = browserPreferencesStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowserPreferences())

    private val _backupState = MutableStateFlow<PreferencesBackupUiState>(PreferencesBackupUiState.Idle)
    val backupState: StateFlow<PreferencesBackupUiState> = _backupState.asStateFlow()

    fun updateShowThumbnails(show: Boolean) {
        viewModelScope.launch {
            val current = browserPreferences.value.globalPresentation
            browserPreferencesStore.updateGlobalPresentation(current.copy(showThumbnails = show))
        }
    }

    fun updateHomeRecentCarouselLimit(limit: Int) {
        viewModelScope.launch {
            browserPreferencesStore.updateHomeRecentCarouselLimit(limit)
        }
    }

    fun updateShowHiddenFiles(show: Boolean) {
        viewModelScope.launch {
            browserPreferencesStore.updateShowHiddenFiles(show)
        }
    }

    suspend fun resetOnboarding() {
        onboardingPreferencesStore.resetOnboarding()
    }

    fun exportPreferences(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = PreferencesBackupUiState.Busy
            preferencesBackupManager.exportTo(uri).fold(
                onSuccess = { result -> _backupState.value = PreferencesBackupUiState.Exported(result) },
                onFailure = { error -> _backupState.value = PreferencesBackupUiState.Failed(error.message ?: "Failed to export settings backup") }
            )
        }
    }

    fun previewRestore(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = PreferencesBackupUiState.Busy
            preferencesBackupManager.preview(uri).fold(
                onSuccess = { preview -> _backupState.value = PreferencesBackupUiState.RestorePreview(uri, preview) },
                onFailure = { error -> _backupState.value = PreferencesBackupUiState.Failed(error.message ?: "Failed to read settings backup") }
            )
        }
    }

    fun restorePreferences(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = PreferencesBackupUiState.Busy
            preferencesBackupManager.restoreFrom(uri).fold(
                onSuccess = { result -> _backupState.value = PreferencesBackupUiState.Restored(result) },
                onFailure = { error -> _backupState.value = PreferencesBackupUiState.Failed(error.message ?: "Failed to restore settings backup") }
            )
        }
    }

    fun clearBackupState() {
        _backupState.value = PreferencesBackupUiState.Idle
    }
}

sealed interface PreferencesBackupUiState {
    data object Idle : PreferencesBackupUiState
    data object Busy : PreferencesBackupUiState
    data class RestorePreview(val uri: Uri, val preview: PreferencesBackupPreview) : PreferencesBackupUiState
    data class Exported(val result: PreferencesBackupOperationResult) : PreferencesBackupUiState
    data class Restored(val result: PreferencesBackupOperationResult) : PreferencesBackupUiState
    data class Failed(val message: String) : PreferencesBackupUiState
}
