package dev.qtremors.arcile.presentation.ui.components

/**
 * Type-safe sealed class replacing magic string dispatch in [ArcileTopBar].
 */
sealed class TopBarAction {
    data object NewFolder : TopBarAction()
    data object DeleteSelected : TopBarAction()
    data object Rename : TopBarAction()
    data object GridView : TopBarAction()
    data object Copy : TopBarAction()
    data object Cut : TopBarAction()
    data object Share : TopBarAction()
    data object Settings : TopBarAction()
    data object About : TopBarAction()
    data object SelectAll : TopBarAction()
    data object PinToQuickAccess : TopBarAction()
}
