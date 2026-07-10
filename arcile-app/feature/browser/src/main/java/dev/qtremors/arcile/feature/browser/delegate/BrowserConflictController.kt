package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.storage.domain.FileConflict
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class BrowserConflictOwner {
    PASTE,
    ARCHIVE
}

internal data class BrowserConflictState(
    val owner: BrowserConflictOwner? = null,
    val conflicts: PersistentList<FileConflict> = persistentListOf(),
    val isVisible: Boolean = false
)

internal class BrowserConflictController(
    initialState: BrowserConflictState,
    private val onStateChange: (BrowserConflictState) -> Unit
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<BrowserConflictState> = _state.asStateFlow()

    fun show(owner: BrowserConflictOwner, conflicts: List<FileConflict>) {
        publish(
            BrowserConflictState(
                owner = owner,
                conflicts = conflicts.toPersistentList(),
                isVisible = true
            )
        )
    }

    fun dismiss() = publish(BrowserConflictState())

    private fun publish(next: BrowserConflictState) {
        _state.value = next
        onStateChange(next)
    }
}
