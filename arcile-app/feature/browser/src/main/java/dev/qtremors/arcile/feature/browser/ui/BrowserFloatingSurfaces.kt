package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Refresh
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.feature.browser.BrowserUiState
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.ui.FloatingSelectionToolbar
import dev.qtremors.arcile.core.ui.ClipboardToolbar
import dev.qtremors.arcile.core.ui.OperationProgressDetailsSheet
import dev.qtremors.arcile.core.storage.domain.storagePathName
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.menus.ExpandableFabMenu
import dev.qtremors.arcile.core.ui.menus.FabMenuItem
import dev.qtremors.arcile.core.ui.theme.LocalSemanticColors
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle
import dev.qtremors.arcile.core.presentation.formatFileSize

@Composable
internal fun BrowserCreateFab(
    state: BrowserUiState,
    showSearchBar: Boolean,
    isFabExpanded: Boolean,
    fabIconRotation: Float,
    onFabExpandedChange: (Boolean) -> Unit,
    dialogVisibility: BrowserDialogVisibility
) {
    if (state.selectedFiles.isEmpty() &&
        !showSearchBar &&
        !state.isVolumeRootScreen &&
        !state.isCategoryScreen &&
        state.archiveContext == null &&
        state.clipboardState == null &&
        state.activeFileOperation == null &&
        state.activeRecoveryOperation == null
    ) {
        Box(modifier = Modifier.navigationBarsPadding()) {
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                ExpandableFabMenu(
                    isExpanded = isFabExpanded,
                    onToggleExpand = { onFabExpandedChange(!isFabExpanded) },
                    fabIconRotation = fabIconRotation,
                    items = listOf(
                        FabMenuItem(
                            label = stringResource(R.string.new_folder),
                            icon = Icons.Default.CreateNewFolder,
                            onClick = {
                                onFabExpandedChange(false)
                                dialogVisibility.showCreateFolderDialog = true
                            }
                        ),
                        FabMenuItem(
                            label = stringResource(R.string.new_file),
                            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                            onClick = {
                                onFabExpandedChange(false)
                                dialogVisibility.showCreateFileDialog = true
                            }
                        ),
                        FabMenuItem(
                            label = stringResource(R.string.new_synthetic_file),
                            icon = Icons.Default.Extension,
                            onClick = {
                                onFabExpandedChange(false)
                                dialogVisibility.showCreateSyntheticFileDialog = true
                            }
                        )
                    )
                )
            }
        }
    }
}

@Composable
internal fun BrowserFloatingSurfaces(
    state: BrowserUiState,
    scaffoldPadding: PaddingValues,
    isFabExpanded: Boolean,
    onFabExpandedChange: (Boolean) -> Unit,
    dialogVisibility: BrowserDialogVisibility,
    selectionIntents: BrowserSelectionIntents,
    mutationIntents: BrowserMutationIntents,
    clipboardIntents: BrowserClipboardIntents,
    operationIntents: BrowserOperationIntents,
    onOperationSucceeded: () -> Unit,
    onOperationFailed: () -> Unit
) {
    var showDetailedProgressSheet by remember { mutableStateOf(false) }
    val activeOperationIdentity = state.activeFileOperation?.let { operation ->
        listOf(operation.type, operation.startTimeMillis, operation.sourcePaths).joinToString("|")
    }

    LaunchedEffect(activeOperationIdentity) {
        showDetailedProgressSheet = false
    }

    if (isFabExpanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onFabExpandedChange(false) }
                )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (state.selectedFiles.isNotEmpty()) {
            BrowserSelectionToolbar(
                state = state,
                dialogVisibility = dialogVisibility,
                selectionIntents = selectionIntents,
                mutationIntents = mutationIntents,
                clipboardIntents = clipboardIntents
            )
        } else if (state.archiveContext != null && state.activeFileOperation == null) {
            BrowserArchiveToolbar(dialogVisibility = dialogVisibility)
        } else if (state.activeRecoveryOperation != null) {
            BrowserRecoveryToolbar(state = state, operationIntents = operationIntents)
        } else if (state.clipboardState != null || state.activeFileOperation != null) {
            BrowserClipboardOperationToolbar(
                state = state,
                dialogVisibility = dialogVisibility,
                clipboardIntents = clipboardIntents,
                onOperationSucceeded = onOperationSucceeded,
                onOperationFailed = onOperationFailed,
                onProgressClick = { showDetailedProgressSheet = true }
            )
        }
    }

    val operation = state.activeFileOperation
    if (showDetailedProgressSheet && operation != null) {
        OperationProgressDetailsSheet(
            totalItems = operation.totalItems,
            completedItems = operation.completedItems,
            onCancel = clipboardIntents.onCancelClipboard,
            onDismissRequest = { showDetailedProgressSheet = false },
            bytesProcessed = operation.bytesProcessed,
            totalBytes = operation.totalBytes,
            startTimeMillis = operation.startTimeMillis,
            queueNames = remember(operation.sourcePaths) { operation.sourcePaths.map(::storagePathName) },
            currentQueueIndex = operation.sourcePaths.indexOfFirst { it == operation.currentPath }
        )
    }
}

@Composable
private fun BrowserArchiveToolbar(
    dialogVisibility: BrowserDialogVisibility
) {
    FloatingSelectionToolbar(
        isVisible = true,
        actions = listOf(
            ToolbarAction(
                icon = Icons.Default.Unarchive,
                contentDescription = stringResource(R.string.archive_extract_archive),
                onClick = { dialogVisibility.showExtractArchiveDialog = true }
            )
        )
    )
}

@Composable
private fun BrowserClipboardOperationToolbar(
    state: BrowserUiState,
    dialogVisibility: BrowserDialogVisibility,
    clipboardIntents: BrowserClipboardIntents,
    onOperationSucceeded: () -> Unit,
    onOperationFailed: () -> Unit,
    onProgressClick: () -> Unit
) {
    val clipboard = state.clipboardState
    val activeOp = state.activeFileOperation

    LaunchedEffect(activeOp?.terminalStatus) {
        when (activeOp?.terminalStatus) {
            OperationCompletionStatus.SUCCESS -> {
                onOperationSucceeded()
            }
            OperationCompletionStatus.FAILED,
            OperationCompletionStatus.CANCELLED -> {
                onOperationFailed()
            }
            null -> Unit
        }
    }

    val rawProgress = activeOp?.let { operation ->
        val byteProgress = operation.totalBytes
            ?.takeIf { it > 0L }
            ?.let { total -> ((operation.bytesProcessed ?: 0L).toFloat() / total.toFloat()).coerceIn(0f, 1f) }
        val itemProgress = operation.totalItems
            .takeIf { it > 0 }
            ?.let { operation.completedItems.toFloat() / it.toFloat() }
            ?.coerceIn(0f, 1f)
        byteProgress ?: itemProgress
    } ?: 0f
    val displayedProgress = if (activeOp?.terminalStatus != null) 1f else rawProgress
    val successColor = LocalSemanticColors.current.success.copy(alpha = 0.25f)
    val failureColor = MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
    val inProgressColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val progressFillColor = when (activeOp?.terminalStatus) {
        OperationCompletionStatus.SUCCESS -> successColor
        OperationCompletionStatus.FAILED,
        OperationCompletionStatus.CANCELLED -> failureColor
        null -> inProgressColor
    }

    val icon = when {
        activeOp?.type == BulkFileOperationType.MOVE || clipboard?.operation == ClipboardOperation.CUT -> Icons.Default.ContentCut
        activeOp?.type == BulkFileOperationType.DELETE || activeOp?.type == BulkFileOperationType.TRASH -> Icons.Default.Delete
        activeOp?.type == BulkFileOperationType.CREATE_ARCHIVE -> Icons.Default.FolderZip
        activeOp?.type == BulkFileOperationType.EXTRACT_ARCHIVE -> Icons.Default.Unarchive
        activeOp?.type == BulkFileOperationType.CREATE_SYNTHETIC -> Icons.Default.Extension
        activeOp?.type == BulkFileOperationType.SAVE_TO_ARCILE_IMPORT -> Icons.Default.ContentPaste
        else -> Icons.Default.ContentCopy
    }
    val iconTint = if (activeOp?.type == BulkFileOperationType.DELETE || activeOp?.type == BulkFileOperationType.TRASH) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val title = when (activeOp?.type) {
        BulkFileOperationType.CREATE_ARCHIVE -> stringResource(R.string.file_operation_creating_archive)
        BulkFileOperationType.EXTRACT_ARCHIVE -> stringResource(R.string.file_operation_extracting_archive)
        BulkFileOperationType.SAVE_TO_ARCILE_IMPORT -> stringResource(R.string.save_to_arcile_title)
        else -> {
            val itemCount = activeOp?.totalItems ?: clipboard?.files?.size ?: 0
            pluralStringResource(
                if (activeOp == null) R.plurals.clipboard_items_ready_to_paste else R.plurals.clipboard_item_count,
                itemCount, itemCount
            )
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val totalBytes = activeOp?.totalBytes
    val subtitle = when {
        activeOp == null -> formatFileSize(context, clipboard?.totalSize ?: 0L)
        totalBytes != null && totalBytes > 0L ->
            formatFileSize(context, (totalBytes - (activeOp.bytesProcessed ?: 0L)).coerceAtLeast(0L))
        else -> pluralStringResource(
            R.plurals.transfer_progress_items, activeOp.totalItems, activeOp.completedItems, activeOp.totalItems
        )
    }
    ClipboardToolbar(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onStatusClick = {
            if (activeOp != null) onProgressClick()
            else dialogVisibility.showClipboardContents = true
        },
        onPaste = clipboardIntents.onPasteFromClipboard.takeIf { activeOp == null },
        onCancel = clipboardIntents.onCancelClipboard.takeIf { activeOp?.terminalStatus == null },
        progress = displayedProgress.takeIf { activeOp != null },
        progressColor = progressFillColor,
        iconTint = iconTint
    )
}
