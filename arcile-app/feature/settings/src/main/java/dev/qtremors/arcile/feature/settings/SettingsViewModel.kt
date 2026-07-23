package dev.qtremors.arcile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.net.Uri
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupGateway
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupOperationResult
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupPreview
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.GalleryPreferences
import dev.qtremors.arcile.core.storage.domain.GalleryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.RecentFilesPreferences
import dev.qtremors.arcile.core.storage.domain.RecentFilesPreferencesStore
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalStagingCache
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import dev.qtremors.arcile.core.vault.domain.VaultThumbnailCache
import dev.qtremors.arcile.feature.settings.ui.SettingsExternalCacheState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val browserPreferencesStore: BrowserLocationPreferencesStore,
    private val recentFilesPreferencesStore: RecentFilesPreferencesStore,
    private val galleryPreferencesStore: GalleryPreferencesStore,
    private val preferencesBackupManager: PreferencesBackupGateway,
    private val externalStagingCache: ExternalStagingCache,
    private val vaultSecurityPreferences: VaultSecurityPreferences,
    private val vaultThumbnailCache: VaultThumbnailCache,
    private val vaultExternalAccessManager: VaultExternalAccessManager
) : ViewModel() {
    val browserPreferences = combine(
        browserPreferencesStore.locationPreferencesFlow,
        recentFilesPreferencesStore.recentFilesPreferencesFlow,
        galleryPreferencesStore.galleryPreferencesFlow,
        ::SettingsPreferences
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsPreferences())

    private val _backupState = MutableStateFlow<PreferencesBackupUiState>(PreferencesBackupUiState.Idle)
    val backupState: StateFlow<PreferencesBackupUiState> = _backupState.asStateFlow()

    private val _externalCache = MutableStateFlow(SettingsExternalCacheState())
    val externalCache: StateFlow<SettingsExternalCacheState> = _externalCache.asStateFlow()
    private val _vaultSecurity = MutableStateFlow(VaultSecurityUiState())
    val vaultSecurity: StateFlow<VaultSecurityUiState> = _vaultSecurity.asStateFlow()

    init {
        refreshExternalCache()
        viewModelScope.launch {
            vaultSecurityPreferences.settings.collect { settings ->
                _vaultSecurity.value = _vaultSecurity.value.copy(
                    screenshotProtectionEnabled = settings.screenshotProtectionEnabled
                )
            }
        }
        refreshVaultSecurity()
    }

    fun refreshVaultSecurity() {
        viewModelScope.launch {
            _vaultSecurity.value = _vaultSecurity.value.copy(isBusy = true)
            val stats = vaultThumbnailCache.stats().getOrNull()
            _vaultSecurity.value = _vaultSecurity.value.copy(
                encryptedThumbnailFiles = stats?.encryptedFileCount ?: 0,
                encryptedThumbnailBytes = stats?.encryptedBytes ?: 0L,
                activeExternalGrants = vaultExternalAccessManager.activeGrants().size,
                isBusy = false
            )
        }
    }

    fun setScreenshotProtection(enabled: Boolean) {
        viewModelScope.launch { vaultSecurityPreferences.setScreenshotProtectionEnabled(enabled) }
    }

    fun clearVaultThumbnails() {
        if (_vaultSecurity.value.isBusy) return
        viewModelScope.launch {
            _vaultSecurity.value = _vaultSecurity.value.copy(isBusy = true)
            vaultThumbnailCache.clear()
            refreshVaultSecurity()
        }
    }

    fun revokeAllVaultExternalAccess() {
        vaultExternalAccessManager.revokeAll()
        refreshVaultSecurity()
    }

    fun refreshExternalCache() {
        viewModelScope.launch {
            _externalCache.value = _externalCache.value.copy(isBusy = true)
            externalStagingCache.stats().fold(
                onSuccess = { stats ->
                    _externalCache.value = SettingsExternalCacheState(
                        fileCount = stats.fileCount,
                        sizeBytes = stats.sizeBytes,
                        isBusy = false
                    )
                },
                onFailure = { _externalCache.value = _externalCache.value.copy(isBusy = false) }
            )
        }
    }

    fun clearExternalCache() {
        if (_externalCache.value.isBusy) return
        viewModelScope.launch {
            _externalCache.value = _externalCache.value.copy(isBusy = true)
            externalStagingCache.clear().fold(
                onSuccess = { stats ->
                    _externalCache.value = SettingsExternalCacheState(
                        fileCount = stats.fileCount,
                        sizeBytes = stats.sizeBytes,
                        isBusy = false
                    )
                },
                onFailure = { _externalCache.value = _externalCache.value.copy(isBusy = false) }
            )
        }
    }

    fun updateShowThumbnails(show: Boolean) {
        viewModelScope.launch {
            val current = browserPreferences.value.globalPresentation
            browserPreferencesStore.updateGlobalPresentation(current.copy(showThumbnails = show))
        }
    }

    fun updateHomeRecentCarouselLimit(limit: Int) {
        viewModelScope.launch {
            recentFilesPreferencesStore.updateHomeRecentCarouselLimit(limit)
        }
    }

    fun updateShowHiddenFiles(show: Boolean) {
        viewModelScope.launch {
            browserPreferencesStore.updateShowHiddenFiles(show)
        }
    }

    fun updateBrowserScrollbarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            browserPreferencesStore.updateBrowserScrollbarEnabled(enabled)
        }
    }

    fun updateGalleryScrollbarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            galleryPreferencesStore.updateGalleryScrollbarEnabled(enabled)
        }
    }

    fun exportPreferences(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = PreferencesBackupUiState.Busy
            preferencesBackupManager.exportTo(uri).fold(
                onSuccess = { result -> _backupState.value = PreferencesBackupUiState.Exported(result) },
                onFailure = { error ->
                    _backupState.value = PreferencesBackupUiState.Failed(
                        error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.settings_backup_export_failed)
                    )
                }
            )
        }
    }

    fun previewRestore(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = PreferencesBackupUiState.Busy
            preferencesBackupManager.preview(uri).fold(
                onSuccess = { preview -> _backupState.value = PreferencesBackupUiState.RestorePreview(uri, preview) },
                onFailure = { error ->
                    _backupState.value = PreferencesBackupUiState.Failed(
                        error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.settings_backup_read_failed)
                    )
                }
            )
        }
    }

    fun restorePreferences(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = PreferencesBackupUiState.Busy
            preferencesBackupManager.restoreFrom(uri).fold(
                onSuccess = { result -> _backupState.value = PreferencesBackupUiState.Restored(result) },
                onFailure = { error ->
                    _backupState.value = PreferencesBackupUiState.Failed(
                        error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.settings_backup_restore_failed)
                    )
                }
            )
        }
    }

    fun clearBackupState() {
        _backupState.value = PreferencesBackupUiState.Idle
    }
}

internal data class VaultSecurityUiState(
    val screenshotProtectionEnabled: Boolean = true,
    val encryptedThumbnailFiles: Int = 0,
    val encryptedThumbnailBytes: Long = 0L,
    val activeExternalGrants: Int = 0,
    val isBusy: Boolean = true
)

internal data class SettingsPreferences(
    val browser: BrowserLocationPreferences = BrowserLocationPreferences(),
    val recentFiles: RecentFilesPreferences = RecentFilesPreferences(),
    val gallery: GalleryPreferences = GalleryPreferences()
) {
    val globalPresentation: FileListingPreferences
        get() = browser.globalPresentation
    val homeRecentCarouselLimit: Int
        get() = recentFiles.homeCarouselLimit
    val showHiddenFiles: Boolean
        get() = browser.showHiddenFiles
    val browserScrollbarEnabled: Boolean
        get() = browser.scrollbarEnabled
    val galleryScrollbarEnabled: Boolean
        get() = gallery.scrollbarEnabled
}

internal sealed interface PreferencesBackupUiState {
    data object Idle : PreferencesBackupUiState
    data object Busy : PreferencesBackupUiState
    data class RestorePreview(val uri: Uri, val preview: PreferencesBackupPreview) : PreferencesBackupUiState
    data class Exported(val result: PreferencesBackupOperationResult) : PreferencesBackupUiState
    data class Restored(val result: PreferencesBackupOperationResult) : PreferencesBackupUiState
    data class Failed(val message: UiText) : PreferencesBackupUiState
}
