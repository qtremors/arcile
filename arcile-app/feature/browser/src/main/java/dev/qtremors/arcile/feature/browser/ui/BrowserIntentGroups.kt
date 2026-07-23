package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.runtime.Stable
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.feature.browser.ArchiveExtractionTarget

@Stable
internal data class BrowserNavigationIntents(
    val onNavigateBack: () -> Unit,
    val onNavigateTo: (String) -> Unit,
    val onOpenFile: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onSelectFolderTab: (String?) -> Unit,
    val onToggleHiddenFiles: () -> Unit = {}
)

@Stable
internal data class BrowserSelectionIntents(
    val onToggleSelection: (String) -> Unit,
    val onSelectMultiple: (List<String>) -> Unit,
    val onClearSelection: () -> Unit,
    val onShareSelected: () -> Unit,
    val onOpenProperties: () -> Unit,
    val onDismissProperties: () -> Unit,
    val onInvertSelection: (List<String>) -> Unit,
    val onSelectAll: (List<String>) -> Unit,
    val onPinToQuickAccess: (String, String) -> Unit
)

@Stable
internal data class BrowserMutationIntents(
    val onCreateFolder: (String) -> Unit,
    val onCreateFile: (String) -> Unit,
    val onCreateFakeFile: (String, Long) -> Unit,
    val onRequestDeleteSelected: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onTogglePermanentDelete: () -> Unit,
    val onToggleShred: () -> Unit,
    val onDismissDeleteConfirmation: () -> Unit,
    val onRenameFile: (String, String) -> Unit
)

@Stable
internal data class BrowserSearchIntents(
    val onSearchQueryChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onPresentationChange: (FileListingPreferences, Boolean) -> Unit,
    val onClearError: () -> Unit,
    val onSearchFiltersChange: (SearchFilters) -> Unit,
    val onToggleSearchFilterMenu: (Boolean) -> Unit
)

@Stable
internal data class BrowserClipboardIntents(
    val onCopySelected: () -> Unit,
    val onCutSelected: () -> Unit,
    val onPasteFromClipboard: () -> Unit,
    val onCancelClipboard: () -> Unit,
    val onRemoveFromClipboard: (String) -> Unit,
    val onResolvingConflicts: (Map<String, ConflictResolution>) -> Unit,
    val onDismissConflictDialog: () -> Unit
)

@Stable
internal data class BrowserArchiveIntents(
    val onExtractArchive: (ArchiveExtractionTarget, String?) -> Unit,
    val onExtractSelectedArchiveEntries: (ArchiveExtractionTarget, String?) -> Unit,
    val onExtractCurrentArchiveFolder: (ArchiveExtractionTarget, String?) -> Unit,
    val onCreateZipFromSelection: () -> Unit,
    val onCreateArchiveFromSelection: (
        String,
        ArchiveFormat,
        ArchiveCompressionLevel,
        String?
    ) -> Unit,
    val onSubmitArchivePassword: (String) -> Unit,
    val onDismissArchivePassword: () -> Unit
)

@Stable
internal data class BrowserOperationIntents(
    val onClearFileOperationStatusMessage: () -> Unit,
    val onClearActiveFileOperation: () -> Unit,
    val onUndoLastTrashMove: () -> Unit,
    val onClearPendingTrashUndo: () -> Unit,
    val onUndoLastOperation: () -> Unit,
    val onClearPendingUndo: () -> Unit,
    val onRetryRecoveredOperation: (String) -> Unit,
    val onCleanupRecoveredOperation: (String) -> Unit,
    val onDismissRecoveredOperation: (String) -> Unit
)

@Stable
internal data class BrowserIntents(
    val navigation: BrowserNavigationIntents,
    val selection: BrowserSelectionIntents,
    val mutation: BrowserMutationIntents,
    val search: BrowserSearchIntents,
    val clipboard: BrowserClipboardIntents,
    val archive: BrowserArchiveIntents,
    val operation: BrowserOperationIntents
)
