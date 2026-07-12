package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.SelectionReducer
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.feature.browser.BrowserSelectionState
import dev.qtremors.arcile.feature.browser.calculateBrowserSelectionSize
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BrowserSelectionContext(
    val isVolumeRootScreen: Boolean,
    val files: List<FileModel>,
    val folderStats: Map<String, FolderStats>
)

internal class SelectionController(
    initialState: BrowserSelectionState,
    private val contextProvider: () -> BrowserSelectionContext,
    private val onSelectionChanged: () -> Unit
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<BrowserSelectionState> = _state.asStateFlow()

    fun toggle(path: String) {
        if (contextProvider().isVolumeRootScreen) return
        update(SelectionReducer.toggle(state.value.selectedFiles, path))
    }

    fun selectAll(paths: List<String>) {
        if (contextProvider().isVolumeRootScreen) return
        update(SelectionReducer.all(paths))
    }

    fun invert(allPaths: List<String>) {
        if (contextProvider().isVolumeRootScreen) return
        update(SelectionReducer.invert(state.value.selectedFiles, allPaths))
    }

    fun selectMultiple(paths: List<String>) {
        if (contextProvider().isVolumeRootScreen) return
        update(SelectionReducer.add(state.value.selectedFiles, paths))
    }

    fun clear() {
        update(emptySet())
    }

    private fun update(selectedPaths: Set<String>) {
        val context = contextProvider()
        val next = BrowserSelectionState(
            selectedFiles = selectedPaths.toPersistentSet(),
            selectedFilesTotalSize = calculateBrowserSelectionSize(
                selectedPaths,
                context.files,
                context.folderStats
            )
        )
        _state.value = next
        onSelectionChanged()
    }
}
