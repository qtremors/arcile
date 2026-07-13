package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.storagePathName
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.BrowserUiState
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.FloatingSelectionToolbar
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle

@Composable
internal fun BrowserRecoveryToolbar(
    state: BrowserUiState,
    operationIntents: BrowserOperationIntents
) {
    val recovery = state.activeRecoveryOperation ?: return
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.file_operation_recovery_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = recoverySummary(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.86f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val onDismissClick = { operationIntents.onDismissRecoveredOperation(recovery.operationId) }
                OutlinedButton(
                    onClick = onDismissClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = onDismissClick)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.file_operation_recovery_dismiss))
                }
                val onCleanupClick = { operationIntents.onCleanupRecoveredOperation(recovery.operationId) }
                OutlinedButton(
                    onClick = onCleanupClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = onCleanupClick)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.file_operation_recovery_cleanup))
                }
                val onRetryClick = { operationIntents.onRetryRecoveredOperation(recovery.operationId) }
                Button(
                    onClick = onRetryClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = onRetryClick)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.file_operation_recovery_retry))
                }
            }
        }
    }
}

@Composable
private fun recoverySummary(state: BrowserUiState): String {
    val recovery = state.activeRecoveryOperation ?: return ""
    val operationName = when (recovery.type) {
        BulkFileOperationType.COPY -> stringResource(R.string.file_operation_copying_files)
        BulkFileOperationType.MOVE -> stringResource(R.string.file_operation_moving_files)
        BulkFileOperationType.TRASH -> stringResource(R.string.file_operation_moving_files_to_trash)
        BulkFileOperationType.DELETE -> stringResource(R.string.file_operation_deleting_files)
        BulkFileOperationType.SHRED -> stringResource(R.string.file_operation_shredding_files)
        BulkFileOperationType.CREATE_FAKE -> stringResource(R.string.file_operation_creating_fake_file)
        BulkFileOperationType.EXTRACT_ARCHIVE -> stringResource(R.string.file_operation_extracting_archive)
        BulkFileOperationType.CREATE_ARCHIVE -> stringResource(R.string.file_operation_creating_archive)
        BulkFileOperationType.SAVE_TO_ARCILE_IMPORT -> stringResource(R.string.save_to_arcile_title)
    }
    val current = recovery.currentPath?.let(::storagePathName)?.takeIf(String::isNotBlank)
    val progress = stringResource(
        R.string.transfer_progress_items,
        recovery.completedItems,
        recovery.totalItems
    )
    return listOfNotNull(
        stringResource(R.string.file_operation_recovery_body, operationName, progress),
        current?.let { stringResource(R.string.file_operation_recovery_current_file, it) }
    ).joinToString(" ")
}

@Composable
internal fun BrowserSelectionToolbar(
    state: BrowserUiState,
    dialogVisibility: BrowserDialogVisibility,
    selectionIntents: BrowserSelectionIntents,
    mutationIntents: BrowserMutationIntents,
    clipboardIntents: BrowserClipboardIntents
) {
    val isArchiveSelection = state.archiveContext != null
    val selectedArchive = state.selectedFiles.singleOrNull()?.let(ArchiveFormat::isSupported) == true
    val mainActions = mutableListOf<ToolbarAction>()
    if (isArchiveSelection) {
        mainActions += ToolbarAction(
            icon = Icons.Default.Unarchive,
            contentDescription = stringResource(R.string.archive_extract_archive),
            onClick = { dialogVisibility.showExtractArchiveDialog = true }
        )
    } else {
        mainActions += ToolbarAction(
            icon = Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.action_copy),
            onClick = clipboardIntents.onCopySelected
        )
        mainActions += ToolbarAction(
            icon = Icons.Default.ContentCut,
            contentDescription = stringResource(R.string.action_cut),
            onClick = clipboardIntents.onCutSelected
        )
        mainActions += ToolbarAction(
            icon = Icons.Default.Delete,
            contentDescription = stringResource(R.string.action_delete_selected),
            tint = MaterialTheme.colorScheme.error,
            onClick = mutationIntents.onRequestDeleteSelected
        )
        if (state.selectedFiles.size == 1) {
            mainActions += ToolbarAction(
                icon = Icons.Default.Edit,
                contentDescription = stringResource(R.string.action_rename),
                onClick = { dialogVisibility.showRenameDialog = true }
            )
        }
    }

    FloatingSelectionToolbar(
        isVisible = true,
        actions = mainActions,
        moreContent = {
            var showSelectionMenu by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            Box {
                Surface(
                    onClick = { showSelectionMenu = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 4.dp,
                    tonalElevation = 4.dp,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more_options),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                DropdownMenu(
                    shape = MaterialTheme.shapes.extraLarge,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    expanded = showSelectionMenu,
                    onDismissRequest = { showSelectionMenu = false }
                ) {
                    val menuActions = remember(
                        selectionIntents.onShareSelected,
                        state.selectedFiles,
                        isArchiveSelection
                    ) {
                        mutableListOf<@Composable () -> Unit>().apply {
                            if (!isArchiveSelection) add {
                                ArcileDropdownMenuItem(
                                    text = { Text(stringResource(R.string.archive_compress_zip)) },
                                    leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        dialogVisibility.showCreateArchiveDialog = true
                                    }
                                )
                            }
                            if (selectedArchive) add {
                                ArcileDropdownMenuItem(
                                    text = { Text(stringResource(R.string.archive_extract_here)) },
                                    leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        dialogVisibility.showExtractArchiveDialog = true
                                    }
                                )
                            }
                            if (!isArchiveSelection) add {
                                ArcileDropdownMenuItem(
                                    text = { Text(stringResource(R.string.share)) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        selectionIntents.onShareSelected()
                                    }
                                )
                            }
                            add {
                                ArcileDropdownMenuItem(
                                    text = { Text(stringResource(R.string.properties_title)) },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        showSelectionMenu = false
                                        selectionIntents.onOpenProperties()
                                    }
                                )
                            }
                        }
                    }
                    menuActions.forEachIndexed { index, action ->
                        val shape = when {
                            menuActions.size == 1 -> MaterialTheme.shapes.menuGroupSingle
                            index == 0 -> MaterialTheme.shapes.menuGroupFirst
                            index == menuActions.lastIndex -> MaterialTheme.shapes.menuGroupLast
                            else -> MaterialTheme.shapes.menuGroupMiddle
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            action()
                        }
                    }
                }
            }
        }
    )
}
