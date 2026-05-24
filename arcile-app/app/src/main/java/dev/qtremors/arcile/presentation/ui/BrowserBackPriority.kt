package dev.qtremors.arcile.presentation.ui

enum class BrowserBackAction {
    CloseModal,
    CloseSheet,
    CloseSearch,
    ClearSelection,
    NavigateFolderUp,
    PopRoute,
    ExitApp
}

data class BrowserBackState(
    val hasModal: Boolean = false,
    val hasSheet: Boolean = false,
    val hasSearch: Boolean = false,
    val hasSelection: Boolean = false,
    val canNavigateFolderUp: Boolean = false,
    val canPopRoute: Boolean = false
)

fun resolveBrowserBackAction(state: BrowserBackState): BrowserBackAction = when {
    state.hasModal -> BrowserBackAction.CloseModal
    state.hasSheet -> BrowserBackAction.CloseSheet
    state.hasSearch -> BrowserBackAction.CloseSearch
    state.hasSelection -> BrowserBackAction.ClearSelection
    state.canNavigateFolderUp -> BrowserBackAction.NavigateFolderUp
    state.canPopRoute -> BrowserBackAction.PopRoute
    else -> BrowserBackAction.ExitApp
}
