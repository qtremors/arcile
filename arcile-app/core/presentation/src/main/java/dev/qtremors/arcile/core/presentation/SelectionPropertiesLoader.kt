package dev.qtremors.arcile.core.presentation

import dev.qtremors.arcile.core.storage.domain.SelectionPropertiesRepository
import dev.qtremors.arcile.core.presentation.PropertiesUiModel
import dev.qtremors.arcile.core.presentation.toUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class SelectionPropertiesUiState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val properties: PropertiesUiModel? = null
)

class SelectionPropertiesLoader(
    private val scope: CoroutineScope,
    private val repository: SelectionPropertiesRepository,
    private val onStateChange: (SelectionPropertiesUiState) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val transform: suspend (List<String>, PropertiesUiModel) -> PropertiesUiModel = { _, value -> value }
) {
    private var state = SelectionPropertiesUiState()
    private var requestGeneration = 0L
    private var requestJob: Job? = null

    fun open(paths: List<String>) {
        if (paths.isEmpty()) return
        val generation = ++requestGeneration
        requestJob?.cancel()
        publish(SelectionPropertiesUiState(isVisible = true, isLoading = true))
        requestJob = scope.launch {
            repository.getSelectionProperties(paths)
                .mapCatching { transform(paths, it.toUiModel()) }
                .fold(
                onSuccess = { properties ->
                    if (generation == requestGeneration) {
                        publish(
                            SelectionPropertiesUiState(
                                isVisible = true,
                                properties = properties
                            )
                        )
                    }
                },
                onFailure = { error ->
                    if (generation == requestGeneration) {
                        publish(SelectionPropertiesUiState())
                        onError(error)
                    }
                }
            )
        }
    }

    fun dismiss() {
        requestGeneration += 1
        requestJob?.cancel()
        requestJob = null
        publish(SelectionPropertiesUiState())
    }

    private fun publish(next: SelectionPropertiesUiState) {
        if (state == next) return
        state = next
        onStateChange(next)
    }
}
