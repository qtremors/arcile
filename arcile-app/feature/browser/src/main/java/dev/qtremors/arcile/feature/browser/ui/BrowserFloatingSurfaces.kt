package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.delay

private const val PROGRESS_PILL_TERMINAL_HOLD_MS = 800L
private const val PROGRESS_PILL_WIDTH_DP = 192

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
                            label = stringResource(R.string.new_fake_file),
                            icon = Icons.Default.Extension,
                            onClick = {
                                onFabExpandedChange(false)
                                dialogVisibility.showCreateFakeFileDialog = true
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
                operationIntents = operationIntents,
                onOperationSucceeded = onOperationSucceeded,
                onOperationFailed = onOperationFailed,
                onProgressClick = { showDetailedProgressSheet = true }
            )
        }
    }

    if (showDetailedProgressSheet && state.activeFileOperation != null) {
        OperationProgressDetailsSheet(
            activeOp = state.activeFileOperation,
            clipboardIntents = clipboardIntents,
            onDismissRequest = { showDetailedProgressSheet = false }
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
    operationIntents: BrowserOperationIntents,
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
                delay(PROGRESS_PILL_TERMINAL_HOLD_MS)
                operationIntents.onClearActiveFileOperation()
            }
            OperationCompletionStatus.FAILED,
            OperationCompletionStatus.CANCELLED -> {
                onOperationFailed()
                delay(PROGRESS_PILL_TERMINAL_HOLD_MS)
                operationIntents.onClearActiveFileOperation()
            }
            null -> Unit
        }
    }

    val hasActiveProgress = activeOp != null
    val rawProgress = activeOp?.let { operation ->
        val byteProgress = operation.totalBytes
            ?.takeIf { it > 0L }
            ?.let { total -> ((operation.bytesCopied ?: 0L).toFloat() / total.toFloat()).coerceIn(0f, 1f) }
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

    val toolbarActions = if (activeOp != null && activeOp.terminalStatus == null) {
        listOf(
            ToolbarAction(
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_cancel_transfer),
                containerColor = MaterialTheme.colorScheme.error,
                tint = Color.White,
                onClick = clipboardIntents.onCancelClipboard
            )
        )
    } else if (activeOp == null) {
        listOf(
            ToolbarAction(
                icon = Icons.Default.ContentPaste,
                contentDescription = stringResource(R.string.action_paste_here),
                onClick = clipboardIntents.onPasteFromClipboard
            ),
            ToolbarAction(
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_cancel_transfer),
                containerColor = MaterialTheme.colorScheme.error,
                tint = Color.White,
                onClick = clipboardIntents.onCancelClipboard
            )
        )
    } else {
        emptyList()
    }

    FloatingSelectionToolbar(
        isVisible = true,
        actions = toolbarActions,
        startContent = {
            Surface(
                onClick = {
                    if (activeOp != null) {
                        onProgressClick()
                    } else {
                        dialogVisibility.showClipboardContents = true
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .height(56.dp)
                    .padding(end = 8.dp)
                    .width(PROGRESS_PILL_WIDTH_DP.dp)
                    .animateContentSize()
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (hasActiveProgress) {
                                Modifier.drawBehind {
                                    val fillWidth = size.width * displayedProgress
                                    drawRect(
                                        color = progressFillColor,
                                        size = Size(fillWidth, size.height)
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val opIcon = when {
                            activeOp?.type == BulkFileOperationType.MOVE || clipboard?.operation == ClipboardOperation.CUT -> Icons.Default.ContentCut
                            activeOp?.type == BulkFileOperationType.DELETE || activeOp?.type == BulkFileOperationType.TRASH -> Icons.Default.Delete
                            activeOp?.type == BulkFileOperationType.CREATE_ARCHIVE -> Icons.Default.FolderZip
                            activeOp?.type == BulkFileOperationType.EXTRACT_ARCHIVE -> Icons.Default.Unarchive
                            activeOp?.type == BulkFileOperationType.CREATE_FAKE -> Icons.Default.Extension
                            activeOp?.type == BulkFileOperationType.SAVE_TO_ARCILE_IMPORT -> Icons.Default.ContentPaste
                            else -> Icons.Default.ContentCopy
                        }
                        val opTint = if (activeOp?.type == BulkFileOperationType.DELETE || activeOp?.type == BulkFileOperationType.TRASH) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        Icon(
                            imageVector = opIcon,
                            contentDescription = null,
                            tint = opTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            val operationTitle = when (activeOp?.type) {
                                BulkFileOperationType.CREATE_ARCHIVE -> stringResource(R.string.file_operation_creating_archive)
                                BulkFileOperationType.EXTRACT_ARCHIVE -> stringResource(R.string.file_operation_extracting_archive)
                                BulkFileOperationType.SAVE_TO_ARCILE_IMPORT -> stringResource(R.string.save_to_arcile_title)
                                else -> {
                                    val itemCount = activeOp?.totalItems ?: clipboard?.files?.size ?: 0
                                    pluralStringResource(R.plurals.clipboard_item_count, itemCount, itemCount)
                                }
                            }
                            Text(
                                text = operationTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val subtitle = if (activeOp != null) {
                                if (activeOp.totalBytes != null && activeOp.totalBytes!! > 0L) {
                                    val remaining = activeOp.totalBytes!! - (activeOp.bytesCopied ?: 0L)
                                    formatFileSize(remaining.coerceAtLeast(0L))
                                } else {
                                    stringResource(
                                        R.string.transfer_progress_items,
                                        activeOp.completedItems,
                                        activeOp.totalItems
                                    )
                                }
                            } else {
                                formatFileSize(clipboard?.totalSize ?: 0L)
                            }

                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
}
