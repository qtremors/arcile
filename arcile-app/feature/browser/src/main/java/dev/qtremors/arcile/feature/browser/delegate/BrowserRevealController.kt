package dev.qtremors.arcile.feature.browser.delegate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BrowserRevealState(
    val path: String? = null,
    val isReady: Boolean = false
)

internal class BrowserRevealController(
    initialState: BrowserRevealState
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<BrowserRevealState> = _state.asStateFlow()

    fun request(path: String) = publish(BrowserRevealState(path = path))

    fun arm() {
        if (state.value.path != null) publish(state.value.copy(isReady = true))
    }

    fun consume(path: String) {
        if (state.value.path == path) publish(BrowserRevealState())
    }

    private fun publish(next: BrowserRevealState) {
        _state.value = next
    }
}
