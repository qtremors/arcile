package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.R
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.FileRepository
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
    private val repository: FileRepository
) {
    fun openPropertiesForSelection() {
        val selectedPaths = state.value.selectedFiles.toList()
        if (selectedPaths.isEmpty()) return

        state.update {
            it.copy(
                isPropertiesVisible = true,
                isPropertiesLoading = true,
                properties = null
            )
        }

        viewModelScope.launch {
            repository.getSelectionProperties(selectedPaths).onSuccess { properties ->
                val archiveSummary = selectedPaths.singleOrNull()
                    ?.takeIf { ArchiveFormat.isSupported(it) }
                    ?.let { repository.getArchiveMetadata(it).getOrNull() }
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
