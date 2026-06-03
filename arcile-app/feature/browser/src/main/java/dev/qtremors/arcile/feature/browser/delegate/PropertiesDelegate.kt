package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.shared.presentation.toUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PropertiesDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val viewModelScope: CoroutineScope,
    private val fileBrowserRepository: FileBrowserRepository,
    private val archiveRepository: ArchiveRepository
) {
    fun openPropertiesForSelection() {
        val selectedPaths = state.value.selectedFiles.toList()
        if (selectedPaths.isEmpty()) return

        val archiveContext = state.value.archiveContext
        if (archiveContext != null) {
            val selectedFiles = state.value.files.filter { it.absolutePath in selectedPaths }
            val modified = selectedFiles.map { it.lastModified }.filter { it > 0L }
            val properties = SelectionProperties(
                displayName = selectedFiles.singleOrNull()?.name ?: "${selectedFiles.size} items",
                pathSummary = listOfNotNull(archiveContext.archiveName, archiveContext.entryPrefix).joinToString("/"),
                itemCount = selectedFiles.size,
                fileCount = selectedFiles.count { !it.isDirectory },
                folderCount = selectedFiles.count { it.isDirectory },
                totalBytes = selectedFiles.filterNot { it.isDirectory }.sumOf { it.size },
                newestModifiedAt = modified.maxOrNull(),
                oldestModifiedAt = modified.minOrNull(),
                mimeTypeSummary = null,
                extensionSummary = selectedFiles
                    .filterNot { it.isDirectory }
                    .map { it.extension }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() },
                hiddenCount = selectedFiles.count { it.isHidden },
                accessStatus = PropertiesAccessStatus.Full,
                isSingleItem = selectedFiles.size == 1,
                isDirectory = selectedFiles.singleOrNull()?.isDirectory
            )
            state.update {
                it.copy(
                    isPropertiesVisible = true,
                    isPropertiesLoading = false,
                    properties = properties.toUiModel()
                )
            }
            return
        }

        state.update {
            it.copy(
                isPropertiesVisible = true,
                isPropertiesLoading = true,
                properties = null
            )
        }

        viewModelScope.launch {
            fileBrowserRepository.getSelectionProperties(selectedPaths).onSuccess { properties ->
                val archiveSummary = selectedPaths.singleOrNull()
                    ?.takeIf { ArchiveFormat.isSupported(it) }
                    ?.let { archiveRepository.getArchiveMetadata(it).getOrNull() }
                state.update {
                    it.copy(
                        isPropertiesVisible = true,
                        isPropertiesLoading = false,
                        properties = properties.toUiModel().copy(archiveSummary = archiveSummary)
                    )
                }
            }.onFailure { error ->
                state.update {
                    it.copy(
                        isPropertiesVisible = false,
                        isPropertiesLoading = false,
                        properties = null,
                        error = error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_load_properties_failed)
                    )
                }
            }
        }
    }

    fun dismissProperties() {
        state.update {
            it.copy(
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }
}
