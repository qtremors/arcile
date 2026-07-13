package dev.qtremors.arcile.feature.trash

import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.storage.domain.FileModel

internal data class TrashNavigationActions(
    val navigateBack: () -> Unit
)

internal data class TrashSelectionActions(
    val toggle: (String) -> Unit,
    val clear: () -> Unit,
    val selectAll: () -> Unit,
    val openProperties: () -> Unit,
    val dismissProperties: () -> Unit
)

internal data class TrashFileActions(
    val open: (FileModel) -> Unit,
    val openWith: (FileModel) -> Unit,
    val shareSelected: () -> Unit
)

internal data class TrashRestoreActions(
    val restoreSelected: () -> Unit,
    val dismissDestinationPicker: () -> Unit,
    val restoreToDestination: (List<String>, String) -> Unit,
    val undoLastRestore: () -> Unit,
    val clearPendingUndo: () -> Unit
)

internal data class TrashDeleteActions(
    val emptyTrash: () -> Unit,
    val permanentlyDeleteSelected: () -> Unit,
    val dismissPermanentDelete: () -> Unit
)

internal data class TrashPresentationActions(
    val searchQueryChange: (String) -> Unit,
    val clearSearch: () -> Unit,
    val sortChange: (TrashSortOption) -> Unit,
    val filterChange: (TrashFilter) -> Unit,
    val refresh: (() -> Unit)?
)

internal data class TrashFeedbackActions(
    val clearError: () -> Unit,
    val clearSnackbarMessage: () -> Unit,
    val feedback: (ArcileFeedbackEvent) -> Unit
)
