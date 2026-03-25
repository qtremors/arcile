package dev.qtremors.arcile.presentation.quickaccess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.data.QuickAccessPreferencesRepository
import dev.qtremors.arcile.domain.QuickAccessItem
import dev.qtremors.arcile.domain.QuickAccessType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class QuickAccessState(
    val items: List<QuickAccessItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class QuickAccessViewModel @Inject constructor(
    private val quickAccessRepository: QuickAccessPreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAccessState())
    val state: StateFlow<QuickAccessState> = _state.asStateFlow()

    init {
        quickAccessRepository.quickAccessItems
            .onEach { items ->
                _state.update { it.copy(items = items, isLoading = false, error = null) }
            }
            .catch { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
            .launchIn(viewModelScope)
    }

    fun togglePin(item: QuickAccessItem) {
        viewModelScope.launch {
            val currentItems = _state.value.items
            val updatedItems = currentItems.map {
                if (it.id == item.id) it.copy(isPinned = !it.isPinned) else it
            }
            quickAccessRepository.updateItems(updatedItems)
        }
    }

    fun removeCustomItem(item: QuickAccessItem) {
        if (item.type == QuickAccessType.STANDARD) return
        viewModelScope.launch {
            quickAccessRepository.removeItem(item.id)
        }
    }

    fun addCustomFolder(path: String, label: String) {
        viewModelScope.launch {
            val newItem = QuickAccessItem(
                id = "custom_${UUID.randomUUID()}",
                label = label,
                path = path,
                type = QuickAccessType.CUSTOM,
                isPinned = true,
                isEnabled = true
            )
            quickAccessRepository.addItem(newItem)
        }
    }

    fun addSafFolder(uriString: String, label: String) {
        viewModelScope.launch {
            val newItem = QuickAccessItem(
                id = "saf_${UUID.randomUUID()}",
                label = label,
                path = uriString,
                type = QuickAccessType.SAF_TREE,
                isPinned = true,
                isEnabled = true
            )
            quickAccessRepository.addItem(newItem)
        }
    }
}
