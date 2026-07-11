package dev.qtremors.arcile.feature.recentfiles

import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.core.presentation.delegate.DeleteStateCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal fun createRecentDeleteController(
    coroutineScope: CoroutineScope,
    volumeRepository: VolumeRepository,
    fileBrowserRepository: FileBrowserRepository,
    bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    state: MutableStateFlow<RecentFilesState>,
    reload: () -> Unit
): DeleteFlowDelegate = DeleteFlowDelegate(
    coroutineScope = coroutineScope,
    volumeRepository = volumeRepository,
    fileBrowserRepository = fileBrowserRepository,
    callbacks = object : DeleteStateCallbacks {
        override fun getSelectedFiles(): List<String> = state.value.selectedFiles.toList()
        override fun isPermanentDeleteChecked(): Boolean =
            state.value.isPermanentDeleteChecked

        override fun isPermanentDeleteToggleEnabled(): Boolean =
            state.value.isPermanentDeleteToggleEnabled

        override fun setLoading(isLoading: Boolean) {
            state.update { it.copy(isLoading = isLoading) }
        }

        override fun showMixedDeleteExplanation() {
            state.update { it.copy(showMixedDeleteExplanation = true) }
        }

        override fun showPermanentDeleteConfirmation() {
            state.update {
                it.copy(
                    showPermanentDeleteConfirmation = true,
                    isPermanentDeleteChecked = true,
                    isPermanentDeleteToggleEnabled = false
                )
            }
        }

        override fun showTrashConfirmation() {
            state.update {
                it.copy(
                    showTrashConfirmation = true,
                    isPermanentDeleteChecked = false,
                    isPermanentDeleteToggleEnabled = true
                )
            }
        }

        override fun togglePermanentDeleteChecked() {
            state.update {
                it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked)
            }
        }

        override fun isShredChecked(): Boolean = state.value.isShredChecked

        override fun toggleShredChecked() {
            state.update { it.copy(isShredChecked = !it.isShredChecked) }
        }

        override fun dismissDeleteConfirmation() {
            state.update {
                it.copy(
                    showTrashConfirmation = false,
                    showPermanentDeleteConfirmation = false,
                    showMixedDeleteExplanation = false,
                    deleteDecision = null,
                    isShredChecked = false
                )
            }
        }

        override fun setError(error: String) {
            state.update { it.copy(error = UiText.Dynamic(error)) }
        }

        override fun setError(error: UiText) {
            state.update { it.copy(error = error) }
        }

        override fun setDeleteDecision(decision: DeleteDecision) {
            state.update { it.copy(deleteDecision = decision) }
        }

        override fun clearSelection() {
            state.update {
                it.copy(
                    selectedFiles = emptySet(),
                    selectedFilesTotalSize = 0L,
                    isPropertiesVisible = false,
                    isPropertiesLoading = false,
                    properties = null
                )
            }
        }
    },
    startBulkDeleteOperation = { type, selected ->
        bulkFileOperationCoordinator.startOperation(
            type = type,
            sourcePaths = selected,
            destinationPath = null,
            resolutions = emptyMap()
        )
    },
    onFailure = reload
)
