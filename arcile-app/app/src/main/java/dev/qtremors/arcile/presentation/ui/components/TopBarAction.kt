package dev.qtremors.arcile.presentation.ui.components

/**
 * Type-safe sealed class replacing magic string dispatch in [ArcileTopBar].
 */
sealed class TopBarAction {
    data object NewFolder : TopBarAction()
    data object DeleteSelected : TopBarAction()
    data object Rename : TopBarAction()
    data object GridView : TopBarAction()
}
