package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.PersistentList

internal sealed interface BrowserUndoAction {
    data class Trash(val trashIds: PersistentList<String>) : BrowserUndoAction
    data class Rename(val originalPath: String, val renamedPath: String) : BrowserUndoAction
    data class Created(val path: String) : BrowserUndoAction
    data class Moved(val entries: PersistentList<MoveUndoEntry>) : BrowserUndoAction
}

@Immutable
internal data class MoveUndoEntry(
    val originalPath: String,
    val movedPath: String
)
