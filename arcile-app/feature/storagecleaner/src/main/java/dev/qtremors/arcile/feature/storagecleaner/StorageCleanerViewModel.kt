package dev.qtremors.arcile.feature.storagecleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanner
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.isIndexed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageCleanerState(
    val groups: List<CleanerGroup> = CleanerGroupType.entries.map { CleanerGroup(it, emptyList()) },
    val isScanning: Boolean = false,
    val isCleaning: Boolean = false,
    val scannedFiles: Int = 0,
    val isPartial: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: CleanerSuccessMessage? = null
) {
    val totalBytes: Long get() = groups.sumOf { it.totalBytes }
    fun group(type: CleanerGroupType): CleanerGroup =
        groups.firstOrNull { it.type == type } ?: CleanerGroup(type, emptyList())
}

data class CleanerSuccessMessage(
    val cleanedCount: Int,
    val undoTrashIds: List<String> = emptyList()
)

@HiltViewModel
class StorageCleanerViewModel @Inject constructor(
    private val volumeRepository: VolumeRepository,
    private val trashRepository: TrashRepository,
    private val scanner: StorageCleanerScanner
) : ViewModel() {

    private val _state = MutableStateFlow(StorageCleanerState())
    val state: StateFlow<StorageCleanerState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        scan()
    }

    fun scan() {
        scan(clearMessages = true)
    }

    private fun scan(clearMessages: Boolean) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update {
                if (clearMessages) {
                    it.copy(isScanning = true, errorMessage = null, successMessage = null)
                } else {
                    it.copy(isScanning = true, errorMessage = null)
                }
            }
            val volumes = volumeRepository.getStorageVolumes().getOrElse { error ->
                _state.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = error.message.orEmpty()
                    )
                }
                return@launch
            }
            val indexedPaths = volumes.filter { it.kind.isIndexed }.map { it.path }
            if (indexedPaths.isEmpty()) {
                _state.update {
                    it.copy(
                        isScanning = false,
                        groups = CleanerGroupType.entries.map { type -> CleanerGroup(type, emptyList()) },
                        scannedFiles = 0,
                        isPartial = false
                    )
                }
                return@launch
            }

            runCatching { scanner.scan(indexedPaths) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            groups = result.groups,
                            scannedFiles = result.scannedFiles,
                            isPartial = result.isPartial,
                            isScanning = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.update {
                        it.copy(
                            isScanning = false,
                            errorMessage = error.message.orEmpty()
                        )
                    }
                }
        }
    }

    fun clean(paths: List<String>) {
        val uniquePaths = paths.distinct()
        if (uniquePaths.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isCleaning = true, errorMessage = null, successMessage = null) }
            trashRepository.moveToTrash(uniquePaths)
                .onSuccess {
                    val undoIds = trashRepository.getTrashFiles().getOrNull()
                        ?.filter { it.originalPath in uniquePaths }
                        ?.sortedByDescending { it.deletionTime }
                        ?.map { it.id }
                        ?.take(uniquePaths.size)
                        .orEmpty()
                    _state.update { current ->
                        current.copy(
                            isCleaning = false,
                            groups = current.groups.map { group ->
                                group.copy(candidates = group.candidates.filterNot { it.absolutePath in uniquePaths })
                            },
                            successMessage = CleanerSuccessMessage(uniquePaths.size, undoIds)
                        )
                    }
                    scan(clearMessages = false)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isCleaning = false,
                            errorMessage = error.message.orEmpty()
                        )
                    }
                }
        }
    }

    fun undoClean(trashIds: List<String>) {
        if (trashIds.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isCleaning = true, errorMessage = null, successMessage = null) }
            trashRepository.restoreFromTrash(trashIds)
                .onSuccess {
                    _state.update { it.copy(isCleaning = false) }
                    scan(clearMessages = false)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isCleaning = false,
                            errorMessage = error.message.orEmpty()
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
