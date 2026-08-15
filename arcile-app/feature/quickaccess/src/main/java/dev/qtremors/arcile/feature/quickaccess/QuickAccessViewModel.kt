package dev.qtremors.arcile.feature.quickaccess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import dev.qtremors.arcile.core.privilege.PrivilegeCoordinator
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

internal data class QuickAccessState(
    val items: List<QuickAccessItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
internal class QuickAccessViewModel @Inject constructor(
    private val quickAccessRepository: QuickAccessPreferencesStore,
    private val privilegeCoordinator: PrivilegeCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(QuickAccessState())
    val state: StateFlow<QuickAccessState> = _state.asStateFlow()
    val accessState = privilegeCoordinator.state
    private val mutationMutex = Mutex()

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
            mutateItems { currentItems ->
                currentItems.map {
                    if (it.id == item.id) it.copy(isPinned = !it.isPinned) else it
                }
            }
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

    fun addExternalHandoffFolder(uriString: String, label: String) {
        viewModelScope.launch {
            val newItem = QuickAccessItem(
                id = "handoff_${UUID.randomUUID()}",
                label = label,
                path = uriString,
                type = QuickAccessType.EXTERNAL_HANDOFF,
                handoffDescription = "Opens in the Android Files app due to platform restrictions.",
                isPinned = true,
                isEnabled = true
            )
            quickAccessRepository.addItem(newItem)
        }
    }

    fun addFilesAppShortcut(uriString: String) {
        viewModelScope.launch {
            quickAccessRepository.addItem(
                QuickAccessItem(
                    id = "handoff_files_app",
                    label = "Files",
                    path = uriString,
                    type = QuickAccessType.FILES_APP,
                    handoffDescription = "Open the Android Files app.",
                    isPinned = true,
                    isEnabled = true
                )
            )
        }
    }

    fun movePinnedItem(itemId: String, direction: Int) {
        if (direction != -1 && direction != 1) return
        viewModelScope.launch {
            mutateItems { currentItems ->
                val pinnedItems = currentItems.filter(QuickAccessItem::isPinned).toMutableList()
                val currentIndex = pinnedItems.indexOfFirst { it.id == itemId }
                val targetIndex = currentIndex + direction
                if (currentIndex == -1 || targetIndex !in pinnedItems.indices) {
                    currentItems
                } else {
                    pinnedItems.add(targetIndex, pinnedItems.removeAt(currentIndex))
                    pinnedItems + currentItems.filterNot(QuickAccessItem::isPinned)
                }
            }
        }
    }

    fun enableShizuku() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(error = null) }
                privilegeCoordinator.selectMode(PrivilegeMode.SHIZUKU, requestAuthorization = true)
                val shizukuReady = privilegeCoordinator.state.value.isReady &&
                    privilegeCoordinator.state.value.activeBackend ==
                    dev.qtremors.arcile.core.privilege.PrivilegeBackendId.SHIZUKU
                privilegeCoordinator.selectMode(PrivilegeMode.AUTOMATIC, requestAuthorization = false)
                if (!shizukuReady) {
                    _state.update { state ->
                        state.copy(error = "Couldn't enable Shizuku. Make sure it is running and authorized, then try again.")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update { state ->
                    state.copy(error = "Couldn't enable Shizuku. Make sure it is running, then try again.")
                }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun mutateItems(transform: (List<QuickAccessItem>) -> List<QuickAccessItem>) {
        mutationMutex.withLock {
            val currentItems = quickAccessRepository.quickAccessItems.first()
            val updatedItems = transform(currentItems)
            if (updatedItems != currentItems) {
                quickAccessRepository.updateItems(updatedItems)
            }
        }
    }
}
