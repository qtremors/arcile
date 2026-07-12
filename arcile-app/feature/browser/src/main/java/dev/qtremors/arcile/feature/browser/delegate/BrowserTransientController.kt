package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.UiText
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class BrowserBusySource {
    CLIPBOARD,
    MUTATION,
    OPERATION
}

internal data class BrowserTransientState(
    val busySources: PersistentSet<BrowserBusySource> = persistentSetOf(),
    val error: UiText? = null
) {
    val isBusy: Boolean get() = busySources.isNotEmpty()
}

internal class BrowserTransientController(
    initialState: BrowserTransientState = BrowserTransientState()
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<BrowserTransientState> = _state.asStateFlow()

    fun setBusy(source: BrowserBusySource, busy: Boolean) {
        _state.update { current ->
            val updated = if (busy) current.busySources + source else current.busySources - source
            current.copy(busySources = updated.toPersistentSet())
        }
    }

    fun reportError(error: UiText?) {
        _state.update { it.copy(error = error) }
    }

    fun clearError() {
        reportError(null)
    }
}
