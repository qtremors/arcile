package dev.qtremors.arcile.feature.archive

import androidx.compose.runtime.Composable
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction

@Composable
internal fun ArchiveViewerScreen(
    state: ArchiveViewerState,
    navigationActions: ArchiveNavigationActions,
    extractionActions: ArchiveExtractionActions,
    conflictActions: ArchiveConflictActions,
    selectionActions: ArchiveSelectionActions,
    archiveFileModel: FileModel = FileModel(name = "", reference = ""),
    viewerActions: Set<ViewerFileAction> = emptySet(),
    onViewerAction: (ViewerFileAction) -> Unit = {}
) = dev.qtremors.arcile.core.ui.archive.ArchiveViewerScreen(
    state, navigationActions, extractionActions, conflictActions, selectionActions,
    archiveFileModel, viewerActions, onViewerAction
)
