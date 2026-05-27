package dev.qtremors.arcile.feature.storageusage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.data.StorageUsageScanner
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.core.ui.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageUsageUiState(
    val selectedVolumeId: String? = null,
    val rootVolume: StorageVolume? = null,
    val scanState: StorageUsageScanState = StorageUsageScanState.Idle,
    val currentRoot: StorageUsageNode? = null,
    val selectedNode: StorageUsageNode? = null,
    val breadcrumbs: List<StorageUsageNode> = emptyList(),
    val unavailableVolume: StorageVolume? = null,
    val error: UiText? = null
)

@HiltViewModel
class StorageUsageViewModel @Inject constructor(
    private val repository: FileRepository,
    private val scanner: StorageUsageScanner
) : ViewModel() {

    private val _state = MutableStateFlow(StorageUsageUiState())
    val state: StateFlow<StorageUsageUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun load(selectedVolumeId: String?) {
        if (_state.value.selectedVolumeId == selectedVolumeId && _state.value.scanState !is StorageUsageScanState.Idle) {
            return
        }
        _state.update { StorageUsageUiState(selectedVolumeId = selectedVolumeId) }
        refresh()
    }

    fun refresh() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val volumes = repository.getStorageVolumes().getOrElse { error ->
                _state.update { it.copy(scanState = StorageUsageScanState.Error(error.message.orEmpty())) }
                return@launch
            }
            val selectedVolumeId = _state.value.selectedVolumeId
            val volume = selectVolume(volumes, selectedVolumeId)
            if (volume == null) {
                _state.update {
                    it.copy(
                        scanState = StorageUsageScanState.Error("No indexed storage volume is available"),
                        currentRoot = null,
                        selectedNode = null,
                        breadcrumbs = emptyList()
                    )
                }
                return@launch
            }
            if (!volume.kind.isIndexed) {
                _state.update {
                    it.copy(
                        rootVolume = volume,
                        unavailableVolume = volume,
                        scanState = StorageUsageScanState.Idle,
                        currentRoot = null,
                        selectedNode = null,
                        breadcrumbs = emptyList()
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    rootVolume = volume,
                    unavailableVolume = null,
                    scanState = StorageUsageScanState.Loading(
                        dev.qtremors.arcile.core.storage.domain.StorageUsageScanProgress(volume.path, 0, 0L, null)
                    ),
                    currentRoot = null,
                    selectedNode = null,
                    breadcrumbs = emptyList()
                )
            }

            scanner.scanStorageUsage(volume.path).collect { scanState ->
                _state.update { current ->
                    when (scanState) {
                        is StorageUsageScanState.Loaded -> current.copy(
                            scanState = scanState,
                            currentRoot = scanState.root,
                            selectedNode = scanState.root,
                            breadcrumbs = listOf(scanState.root)
                        )
                        else -> current.copy(scanState = scanState)
                    }
                }
            }
        }
    }

    fun selectNode(node: StorageUsageNode) {
        _state.update { it.copy(selectedNode = node) }
    }

    fun drillInto(node: StorageUsageNode) {
        if (!node.isContainer || node.children.isEmpty()) {
            selectNode(node)
            return
        }
        _state.update {
            it.copy(
                currentRoot = node,
                selectedNode = node,
                breadcrumbs = appendBreadcrumb(it.breadcrumbs, node)
            )
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        val breadcrumbs = _state.value.breadcrumbs
        val node = breadcrumbs.getOrNull(index) ?: return
        _state.update {
            it.copy(
                currentRoot = node,
                selectedNode = node,
                breadcrumbs = breadcrumbs.take(index + 1)
            )
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(scanState = StorageUsageScanState.Idle) }
    }

    private fun selectVolume(volumes: List<StorageVolume>, selectedVolumeId: String?): StorageVolume? {
        selectedVolumeId?.let { requested ->
            return volumes.firstOrNull { it.id == requested }
        }
        return volumes.firstOrNull { it.isPrimary && it.kind.isIndexed }
            ?: volumes.firstOrNull { it.kind.isIndexed }
            ?: volumes.firstOrNull { it.kind == StorageKind.INTERNAL }
    }

    private fun appendBreadcrumb(
        breadcrumbs: List<StorageUsageNode>,
        node: StorageUsageNode
    ): List<StorageUsageNode> {
        val existingIndex = breadcrumbs.indexOfFirst { it.path == node.path }
        return if (existingIndex >= 0) {
            breadcrumbs.take(existingIndex + 1)
        } else {
            breadcrumbs + node
        }
    }
}
