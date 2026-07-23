package dev.qtremors.arcile.presentation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.UtilityPreferencesStore
import dev.qtremors.arcile.core.ui.utilities.HomeUtilityCatalog
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class UtilityPreferencesViewModel @Inject constructor(
    private val utilityPreferencesStore: UtilityPreferencesStore
) : ViewModel() {
    private val allowedHomeUtilityIds = HomeUtilityCatalog.mapTo(mutableSetOf()) { it.id }
    private val mutations = Mutex()

    val homeUtilityIds = utilityPreferencesStore.homeUtilityIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("trash", "cleaner"))

    fun setUtilityShownOnHome(id: String, show: Boolean) {
        if (id !in allowedHomeUtilityIds) return
        updateUtilities { current ->
            val next = if (show) {
                current + id
            } else {
                current - id
            }
            next
        }
    }

    fun moveUtility(id: String, direction: Int) {
        if (direction == 0) return
        updateUtilities { current ->
            val from = current.indexOf(id)
            val to = from + direction
            if (from < 0 || to !in current.indices) return@updateUtilities current
            current.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    private fun updateUtilities(transform: (List<String>) -> List<String>) {
        viewModelScope.launch {
            mutations.withLock {
                val current = utilityPreferencesStore.homeUtilityIds.first()
                val next = transform(current).filter { it in allowedHomeUtilityIds }.distinct()
                if (next != current) utilityPreferencesStore.setHomeUtilityIds(next)
            }
        }
    }
}
