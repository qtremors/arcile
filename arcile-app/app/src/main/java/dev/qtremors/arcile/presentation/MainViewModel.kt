package dev.qtremors.arcile.presentation

import android.os.Environment
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val browserPreferencesStore: BrowserLocationPreferencesStore
) : ViewModel() {

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()
    val fileOpenBehaviors: StateFlow<Map<String, FileOpenBehavior>> =
        browserPreferencesStore.locationPreferencesFlow
            .map { it.fileOpenBehaviors }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val appStartPage: StateFlow<AppStartPage?> =
        browserPreferencesStore.locationPreferencesFlow
            .map { it.appStartPage }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        checkPermission()
    }

    fun checkPermission() {
        _hasPermission.value = Environment.isExternalStorageManager()
    }

    fun updateAppStartPage(page: AppStartPage) {
        viewModelScope.launch {
            browserPreferencesStore.updateAppStartPage(page)
        }
    }
}
