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
import androidx.lifecycle.viewModelScope
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    browserPreferencesStore: BrowserLocationPreferencesStore
) : ViewModel() {

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()
    val fileOpenBehaviors: StateFlow<Map<String, FileOpenBehavior>> =
        browserPreferencesStore.locationPreferencesFlow
            .map { it.fileOpenBehaviors }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        checkPermission()
    }

    fun checkPermission() {
        _hasPermission.value = Environment.isExternalStorageManager()
    }
}
