package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.ConflictResolution

@Stable
internal class BrowserDialogVisibility(
    private val createFolderState: MutableState<Boolean>,
    private val renameState: MutableState<Boolean>,
    private val createFileState: MutableState<Boolean>,
    private val createFakeFileState: MutableState<Boolean>,
    private val createArchiveState: MutableState<Boolean>,
    private val extractArchiveState: MutableState<Boolean>,
    private val sortState: MutableState<Boolean>,
    private val clipboardContentsState: MutableState<Boolean>
) {
    var showCreateFolderDialog by createFolderState
    var showRenameDialog by renameState
    var showCreateFileDialog by createFileState
    var showCreateFakeFileDialog by createFakeFileState
    var showCreateArchiveDialog by createArchiveState
    var showExtractArchiveDialog by extractArchiveState
    var showSortDialog by sortState
    var showClipboardContents by clipboardContentsState

    val hasVisibleDialog: Boolean
        get() = showCreateFolderDialog ||
            showCreateFileDialog ||
            showCreateFakeFileDialog ||
            showCreateArchiveDialog ||
            showExtractArchiveDialog ||
            showRenameDialog ||
            showSortDialog ||
            showClipboardContents
}

@Composable
internal fun rememberBrowserDialogVisibility(): BrowserDialogVisibility {
    val createFolderState = rememberSaveable { mutableStateOf(false) }
    val renameState = rememberSaveable { mutableStateOf(false) }
    val createFileState = rememberSaveable { mutableStateOf(false) }
    val createFakeFileState = rememberSaveable { mutableStateOf(false) }
    val createArchiveState = rememberSaveable { mutableStateOf(false) }
    val extractArchiveState = rememberSaveable { mutableStateOf(false) }
    val sortState = rememberSaveable { mutableStateOf(false) }
    val clipboardContentsState = rememberSaveable { mutableStateOf(false) }
    return BrowserDialogVisibility(
        createFolderState = createFolderState,
        renameState = renameState,
        createFileState = createFileState,
        createFakeFileState = createFakeFileState,
        createArchiveState = createArchiveState,
        extractArchiveState = extractArchiveState,
        sortState = sortState,
        clipboardContentsState = clipboardContentsState
    )
}

@Stable
internal data class BrowserUiActions(
    val onNavigateBack: () -> Unit,
    val onNavigateTo: (String) -> Unit,
    val onOpenFile: (String) -> Unit,
    val onToggleSelection: (String) -> Unit,
    val onSelectMultiple: (List<String>) -> Unit,
    val onClearSelection: () -> Unit,
    val onCreateFolder: (String) -> Unit,
    val onCreateFile: (String) -> Unit,
    val onCreateFakeFile: (String, Long) -> Unit,
    val onRequestDeleteSelected: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onTogglePermanentDelete: () -> Unit,
    val onToggleShred: () -> Unit,
    val onDismissDeleteConfirmation: () -> Unit,
    val onRenameFile: (String, String) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onPresentationChange: (BrowserPresentationPreferences, Boolean) -> Unit,
    val onClearError: () -> Unit,
    val onCopySelected: () -> Unit,
    val onCutSelected: () -> Unit,
    val onPasteFromClipboard: () -> Unit,
    val onCancelClipboard: () -> Unit,
    val onShareSelected: () -> Unit,
    val onClearFileOperationStatusMessage: () -> Unit,
    val onOpenProperties: () -> Unit,
    val onDismissProperties: () -> Unit,
    val onClearActiveFileOperation: () -> Unit,
    val onDismissConflictDialog: () -> Unit,
    val onRefresh: () -> Unit,
    val onSearchFiltersChange: (dev.qtremors.arcile.core.storage.domain.SearchFilters) -> Unit,
    val onToggleSearchFilterMenu: (Boolean) -> Unit,
    val onResolvingConflicts: (Map<String, ConflictResolution>) -> Unit,
    val onPinToQuickAccess: (String, String) -> Unit,
    val onNativeRequestResult: (Boolean) -> Unit,
    val onInvertSelection: (List<String>) -> Unit,
    val onSelectAll: (List<String>) -> Unit,
    val onRemoveFromClipboard: (String) -> Unit,
    val onSelectFolderTab: (String?) -> Unit,
    val onExtractArchive: (String?, Boolean, Boolean) -> Unit,
    val onCreateZipFromSelection: () -> Unit,
    val onCreateArchiveFromSelection: (String, ArchiveFormat, String?, Boolean, Boolean) -> Unit,
    val onUndoLastTrashMove: () -> Unit,
    val onClearPendingTrashUndo: () -> Unit,
    val onRetryRecoveredOperation: (String) -> Unit,
    val onCleanupRecoveredOperation: (String) -> Unit,
    val onDismissRecoveredOperation: (String) -> Unit
)
