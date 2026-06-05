package dev.qtremors.arcile.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.UtilityPreferencesStore
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UtilityPreferencesViewModel @Inject constructor(
    private val utilityPreferencesStore: UtilityPreferencesStore
) : ViewModel() {
    private val allowedHomeUtilityIds = HomeUtilityCatalog.mapTo(mutableSetOf()) { it.id }

    val homeUtilityIds = utilityPreferencesStore.homeUtilityIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), setOf("trash", "cleaner"))

    fun setUtilityShownOnHome(id: String, show: Boolean) {
        if (id !in allowedHomeUtilityIds) return
        viewModelScope.launch {
            val next = if (show) {
                homeUtilityIds.value + id
            } else {
                homeUtilityIds.value - id
            }
            utilityPreferencesStore.setHomeUtilityIds(next.filterTo(mutableSetOf()) { it in allowedHomeUtilityIds })
        }
    }
}
