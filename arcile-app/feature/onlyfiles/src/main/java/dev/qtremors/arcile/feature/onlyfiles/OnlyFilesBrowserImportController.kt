package dev.qtremors.arcile.feature.onlyfiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Owns the one-shot Browser handoff. Paths never enter navigation or saved UI state. */
internal class OnlyFilesBrowserImportController(
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val imports: OnlyFilesImportController,
    private val closePicker: () -> Unit,
    private val takeRequest: (String) -> List<String>? = OnlyFilesBrowserImports::take
) {
    private var lastRequest: String? = null
    private var paths: List<String> = emptyList()
    private var moveDestination: OnlyFilesLocationPickerState? = null
    private var moveMount: Long? = null

    val hasPending: Boolean get() = paths.isNotEmpty()

    fun accept(requestId: String) {
        if (lastRequest == requestId) return
        lastRequest = requestId
        paths = takeRequest(requestId).orEmpty()
        moveDestination = null
        moveMount = null
        state.update {
            it.copy(
                browserImportCount = paths.size,
                browserImportMovePending = false,
                message = null,
                operationFeedback = if (paths.isEmpty()) operationNotice(R.string.onlyfiles_browser_import_expired, it.mountGeneration)
                    else it.operationFeedback
            )
        }
    }

    fun choose(move: Boolean = false) {
        val picker = validPicker() ?: return
        if (move) {
            moveDestination = picker
            moveMount = state.value.mountGeneration
            state.update { it.copy(browserImportMovePending = true) }
        } else submit(picker, move = false)
    }

    fun confirmMove(confirmed: Boolean) {
        val picker = moveDestination
        val mount = moveMount
        moveDestination = null
        moveMount = null
        val pending = state.value.browserImportMovePending
        state.update { it.copy(browserImportMovePending = false) }
        if (!confirmed || !pending || mount != state.value.mountGeneration) return
        // Recheck the exact authenticated destination after confirmation and any concurrent UI change.
        if (picker != null && picker == validPicker()) submit(picker, move = true)
    }

    fun clear() {
        paths = emptyList()
        moveDestination = null
        moveMount = null
        state.update { it.copy(browserImportCount = 0, browserImportMovePending = false) }
    }

    private fun validPicker(): OnlyFilesLocationPickerState? {
        val current = state.value
        if (paths.isEmpty() || current.busy || current.selectedVaultId == null || current.activeImports.isNotEmpty()) return null
        return current.locationPicker?.takeIf {
            it.mode == OnlyFilesLocationMode.IMPORT_DESTINATION && !it.loading && !it.failed && it.stack.isNotEmpty()
        }
    }

    private fun submit(picker: OnlyFilesLocationPickerState, move: Boolean) {
        val directory = picker.stack.lastOrNull() ?: return
        if (!imports.begin(directory.path, directory.id)) return
        if (!imports.finishSelection(paths, move)) return
        clear()
        closePicker()
    }
}
