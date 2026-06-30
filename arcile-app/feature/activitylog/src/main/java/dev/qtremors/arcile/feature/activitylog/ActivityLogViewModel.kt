package dev.qtremors.arcile.feature.activitylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.ActivityLogStore
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class ActivityLogViewModel @Inject constructor(
    private val activityLogStore: ActivityLogStore
) : ViewModel() {
    val entries = activityLogStore.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearActivity() {
        viewModelScope.launch { activityLogStore.clear() }
    }
}
